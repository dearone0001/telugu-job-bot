import os
import json
import requests
from bs4 import BeautifulSoup
import time
from datetime import datetime

# Comprehensive list of FreeJobAlert sources to cover "All Jobs"
SOURCES = [
    {"url": "https://www.freejobalert.com/latest-notifications/", "cat": "All"},
    {"url": "https://www.freejobalert.com/central-government-jobs/", "cat": "Central"},
    {"url": "https://www.freejobalert.com/bank-jobs/", "cat": "Banking"},
    {"url": "https://www.freejobalert.com/ssc-jobs/", "cat": "SSC"},
    {"url": "https://www.freejobalert.com/railway-jobs/", "cat": "Railways"},
    {"url": "https://www.freejobalert.com/andhra-pradesh-govt-jobs/", "cat": "State"},
    {"url": "https://www.freejobalert.com/telangana-govt-jobs/", "cat": "State"},
    {"url": "https://www.freejobalert.com/teaching-jobs/", "cat": "State"},
    {"url": "https://www.freejobalert.com/police-jobs/", "cat": "State"},
    {"url": "https://www.freejobalert.com/upsc-jobs/", "cat": "Central"},
    {"url": "https://www.freejobalert.com/defense-jobs/", "cat": "Central"}
]

HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"}

def parse_date(date_str):
    if not date_str or any(x in date_str.upper() for x in ["TBA", "ADVT", "NA"]):
        return ""
    try:
        # Try cleaning the string first (sometimes contains time or extra text)
        clean_date = date_str.split(' ')[0].strip()
        for fmt in ('%d-%m-%Y', '%Y-%m-%d', '%d/%m/%Y'):
            try:
                return datetime.strptime(clean_date, fmt).strftime('%Y-%m-%d')
            except ValueError:
                continue
        return ""
    except:
        return ""

def is_expired(date_str):
    iso_date = parse_date(date_str)
    if not iso_date:
        return False
    try:
        expiry_date = datetime.strptime(iso_date, '%Y-%m-%d')
        return expiry_date.date() < datetime.now().date()
    except:
        return False

def get_job_details(url):
    """Deep crawl for specific notification info"""
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

        # Extract description
        for p in content.find_all('p'):
            text = p.text.strip()
            if len(text) > 80 and "recruitment" in text.lower():
                details["job_description"] = text
                break

        # Extract specific sections
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

        # Build direct links
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
    print(f"Starting Mega-Scrape at {datetime.now()}...")
    scraped_items = []
    seen_titles = set()

    for source in SOURCES:
        try:
            print(f"Syncing: {source['url']}...")
            response = requests.get(source['url'], headers=HEADERS, timeout=15)
            soup = BeautifulSoup(response.text, 'html.parser')

            # Find all relevant tables
            tables = soup.find_all('table', {'class': 'vtable'})
            if not tables: continue

            # Iterate through major notice boards on each page
            for table in tables:
                for row in table.find_all('tr'):
                    cells = row.find_all('td')
                    if len(cells) >= 3:
                        title_cell = cells[0]
                        raw_title = title_cell.text.strip()
                        apply_url = title_cell.find('a')['href'] if title_cell.find('a') else None
                        deadline = cells[2].text.strip()
                        post_date = cells[1].text.strip() if len(cells) > 1 else ""

                        if raw_title in seen_titles: continue
                        if is_expired(deadline): continue

                        if apply_url and any(kwd in raw_title for kwd in ["Notification", "Recruitment", "Jobs", "Apply", "Online"]):
                            print(f"Deep scraping: {raw_title[:35]}...")
                            details = get_job_details(apply_url)

                            iso_deadline = parse_date(deadline) or deadline
                            iso_post_date = parse_date(post_date) or datetime.now().strftime('%Y-%m-%d')

                            # Map to exact app categories
                            cat = source['cat']
                            if "Bank" in raw_title or "IBPS" in raw_title: cat = "Banking"
                            elif "SSC" in raw_title: cat = "SSC"
                            elif "Railway" in raw_title or "RRB" in raw_title: cat = "Railways"
                            elif "UPSC" in raw_title or "Defense" in raw_title: cat = "Central"
                            elif "AP" in raw_title or "TS" in raw_title or "Telangana" in raw_title: cat = "State"

                            scraped_items.append({
                                "title": raw_title,
                                "category": cat,
                                "vacancies": cells[2].text.strip() if len(cells) > 2 else "Check Details",
                                "last_date": iso_deadline,
                                "district": "All India" if cat != "State" else "State Specific",
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
                            time.sleep(0.5) # Fast but respectful

                            if len(scraped_items) >= 300: break # Mega capacity
                if len(scraped_items) >= 300: break
        except Exception as e:
            print(f"Error: {e}")

    return scraped_items

if __name__ == "__main__":
    job_list = scrape_jobs()
    if job_list:
        # Sort by post_date descending
        job_list.sort(key=lambda x: x['post_date'], reverse=True)
        with open('jobs.jsonl', 'w', encoding='utf-8') as f:
            for item in job_list:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")
        print(f"SUCCESS: Database synchronized with {len(job_list)} total active jobs!")
