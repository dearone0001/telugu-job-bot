import os
import json
import requests
from bs4 import BeautifulSoup
import time
import re
from datetime import datetime

# Comprehensive list of FreeJobAlert sources
SOURCES = [
    {"url": "https://www.freejobalert.com/latest-notifications/", "cat": "All"},
    {"url": "https://www.freejobalert.com/bank-jobs/", "cat": "Banking"},
    {"url": "https://www.freejobalert.com/ssc-jobs/", "cat": "SSC"},
    {"url": "https://www.freejobalert.com/railway-jobs/", "cat": "Railways"},
    {"url": "https://www.freejobalert.com/andhra-pradesh-govt-jobs/", "cat": "Andhra Pradesh"},
    {"url": "https://www.freejobalert.com/telangana-govt-jobs/", "cat": "Telangana"},
    {"url": "https://www.freejobalert.com/teaching-jobs/", "cat": "State"},
    {"url": "https://www.freejobalert.com/police-jobs/", "cat": "State"},
    {"url": "https://www.freejobalert.com/central-government-jobs/", "cat": "State"},
    {"url": "https://www.freejobalert.com/upsc-jobs/", "cat": "State"},
    {"url": "https://www.freejobalert.com/defense-jobs/", "cat": "State"}
]

HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"}

def extract_latest_date(text):
    """
    Scans text for all date patterns (DD-MM-YYYY, YYYY-MM-DD, etc.)
    and returns the latest one found. Handles cases like '15-03-2025 extended to 31-03-2025'.
    """
    if not text or any(x in text.upper() for x in ["TBA", "ADVT", "NA"]):
        return None

    # Pattern for common date formats in job alerts
    date_patterns = [
        r'\d{1,2}-\d{1,2}-\d{4}',
        r'\d{4}-\d{1,2}-\d{1,2}',
        r'\d{1,2}/\d{1,2}/\d{4}'
    ]

    found_dates = []
    for pattern in date_patterns:
        matches = re.findall(pattern, text)
        for m in matches:
            for fmt in ('%d-%m-%Y', '%Y-%m-%d', '%d/%m/%Y'):
                try:
                    found_dates.append(datetime.strptime(m, fmt))
                    break
                except ValueError:
                    continue

    if not found_dates:
        return None

    return max(found_dates)

def is_expired(date_text):
    """Checks if a job's deadline has strictly passed."""
    latest_expiry = extract_latest_date(date_text)
    if not latest_expiry:
        return False # Fail-open: if no date found, don't hide it

    # Set time to end of day for the expiry date
    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    return latest_expiry < today

def get_job_details(url):
    try:
        response = requests.get(url, headers=HEADERS, timeout=8)
        if response.status_code != 200: return {}
        soup = BeautifulSoup(response.text, 'html.parser')
        content = soup.find('div', {'class': 'post-content'}) or soup.find('article')
        if not content: return {}

        details = {
            "age_limit": "Refer to official notification.",
            "qualification": "Refer to official notification.",
            "pattern_table": "",
            "instructions": "Follow instructions on official portal.",
            "links": "",
            "application_fee": "Check notification for fee details.",
            "selection_process": "Selection based on Test/Interview.",
            "job_description": "Detailed notification available on the official website."
        }

        for p in content.find_all('p'):
            text = p.text.strip()
            if len(text) > 80 and "recruitment" in text.lower():
                details["job_description"] = text
                break

        sections = content.find_all(['h2', 'h3', 'p', 'table', 'li', 'span', 'strong'])
        for i, tag in enumerate(sections):
            text = tag.text.strip().lower()
            if "age limit" in text:
                details["age_limit"] = sections[i+1].text.strip() if i+1 < len(sections) else details["age_limit"]
            elif "qualification" in text:
                details["qualification"] = sections[i+1].text.strip() if i+1 < len(sections) else details["qualification"]
            elif "application fee" in text:
                details["application_fee"] = sections[i+1].text.strip() if i+1 < len(sections) else details["application_fee"]
            elif "selection process" in text:
                details["selection_process"] = sections[i+1].text.strip() if i+1 < len(sections) else details["selection_process"]
            elif tag.name == 'table':
                rows_list = []
                for row in tag.find_all('tr'):
                    cells = [c.text.strip() for c in row.find_all(['td', 'th'])]
                    if cells: rows_list.append("|".join(cells))
                details["pattern_table"] = "\n".join(rows_list)

        links_dict = {}
        for a in content.find_all('a'):
            link_text = a.text.strip().lower()
            href = a.get('href', '')
            if not href or href.startswith('#'): continue
            if href.startswith('/'): href = "https://www.freejobalert.com" + href

            if "notification" in link_text: links_dict["Official Notification"] = href
            elif "apply online" in link_text: links_dict["Apply Online"] = href
            elif "official website" in link_text: links_dict["Official Website"] = href

        if links_dict:
            details["links"] = "|".join([f"{k}:{v}" for k, v in links_dict.items()])
        else:
            details["links"] = f"Official Source:{url}"

        return details
    except:
        return {}

def scrape_jobs():
    print(f"Starting Multi-Portal Scrape at {datetime.now()}...")
    scraped_items = []
    seen_titles = set()

    for source in SOURCES:
        try:
            print(f"Syncing: {source['url']}...")
            response = requests.get(source['url'], headers=HEADERS, timeout=15)
            soup = BeautifulSoup(response.text, 'html.parser')

            tables = soup.find_all('table', {'class': 'vtable'})
            if not tables: continue

            for table in tables:
                for row in table.find_all('tr'):
                    cells = row.find_all('td')
                    if len(cells) >= 3:
                        title_cell = cells[0]
                        raw_title = title_cell.text.strip()
                        apply_url = title_cell.find('a')['href'] if title_cell.find('a') else None
                        deadline_text = cells[2].text.strip()
                        post_date_text = cells[1].text.strip() if len(cells) > 1 else ""

                        if raw_title in seen_titles: continue

                        # Use new robust expiry check
                        if is_expired(deadline_text):
                            print(f"Skipping expired job: {raw_title[:30]} (Deadline: {deadline_text})")
                            continue

                        if apply_url and any(kwd in raw_title for kwd in ["Notification", "Recruitment", "Jobs", "Apply", "Online"]):
                            print(f"Deep scraping: {raw_title[:35]}...")
                            details = get_job_details(apply_url)

                            # Standardize dates
                            latest_deadline = extract_latest_date(deadline_text)
                            latest_post = extract_latest_date(post_date_text)

                            iso_deadline = latest_deadline.strftime('%Y-%m-%d') if latest_deadline else deadline_text
                            iso_post_date = latest_post.strftime('%Y-%m-%d') if latest_post else datetime.now().strftime('%Y-%m-%d')

                            # Map categories
                            cat = source['cat']
                            if "Bank" in raw_title or "IBPS" in raw_title: cat = "Banking"
                            elif "SSC" in raw_title: cat = "SSC"
                            elif "Railway" in raw_title or "RRB" in raw_title: cat = "Railways"

                            dist = "All India"
                            if "AP" in raw_title or "Andhra" in raw_title:
                                dist = "Andhra Pradesh"
                                if cat == "State": cat = "Andhra Pradesh"
                            elif "TS" in raw_title or "Telangana" in raw_title:
                                dist = "Telangana"
                                if cat == "State": cat = "Telangana"

                            scraped_items.append({
                                "title": raw_title,
                                "category": cat,
                                "vacancies": cells[2].text.strip() if len(cells) > 2 else "Check Details",
                                "last_date": iso_deadline,
                                "district": dist,
                                "age_limit": details.get("age_limit", ""),
                                "qualification": details.get("qualification", ""),
                                "pattern_table": details.get("pattern_table", ""),
                                "instructions": details.get("instructions", ""),
                                "links": details.get("links", ""),
                                "application_fee": details.get("application_fee", ""),
                                "selection_process": details.get("selection_process", ""),
                                "post_date": iso_post_date,
                                "job_description": details.get("job_description", "")
                            })
                            seen_titles.add(raw_title)
                            time.sleep(0.5)

                            if len(scraped_items) >= 400: break
                if len(scraped_items) >= 400: break
        except Exception as e:
            print(f"Error: {e}")

    return scraped_items

if __name__ == "__main__":
    job_list = scrape_jobs()
    if job_list:
        job_list.sort(key=lambda x: x['post_date'], reverse=True)
        with open('jobs.jsonl', 'w', encoding='utf-8') as f:
            for item in job_list:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")
        print(f"SUCCESS: Database synchronized with {len(job_list)} total active jobs!")
