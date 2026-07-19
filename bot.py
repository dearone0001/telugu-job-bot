import os
import json
import requests
from bs4 import BeautifulSoup
import time

URL = "https://www.freejobalert.com/central-government-jobs/"
HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"}

def get_job_details(url):
    """Fetch and parse comprehensive details from an individual job page."""
    try:
        response = requests.get(url, headers=HEADERS, timeout=10)
        if response.status_code != 200:
            return {}

        soup = BeautifulSoup(response.text, 'html.parser')
        content = soup.find('div', {'class': 'post-content'}) or soup.find('article')
        if not content:
            return {}

        details = {
            "age_limit": "Refer to official notification.",
            "qualification": "Refer to official notification.",
            "pattern_table": "",
            "instructions": "Follow official website instructions.",
            "links": "",
            "application_fee": "Check notification for fee details.",
            "selection_process": "Written Test / Interview",
            "job_description": "Detailed notification available on the official website."
        }

        sections = content.find_all(['h2', 'h3', 'p', 'table', 'li', 'span', 'strong'])

        # Description extraction
        for p in content.find_all('p'):
            text = p.text.strip()
            if len(text) > 100 and "recruitment" in text.lower():
                details["job_description"] = text
                break

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
                    rows_list.append("|".join(cells))
                details["pattern_table"] = "\n".join(rows_list)

        # Improved Link Extraction
        links_dict = {}
        for a in content.find_all('a'):
            link_text = a.text.strip().lower()
            href = a.get('href', '')
            if not href or href.startswith('#'): continue

            # Absolute URL conversion if needed
            if href.startswith('/'):
                href = "https://www.freejobalert.com" + href

            if "notification" in link_text:
                links_dict["Official Notification"] = href
            elif "apply online" in link_text:
                links_dict["Apply Online"] = href
            elif "official website" in link_text:
                links_dict["Official Website"] = href
            elif "syllabus" in link_text:
                links_dict["Exam Syllabus"] = href

        if links_dict:
            details["links"] = "|".join([f"{k}:{v}" for k, v in links_dict.items()])
        else:
            details["links"] = f"Official Source:{url}"

        return details
    except Exception:
        return {}

def scrape_jobs():
    print("Executing Deep All-India Central JSONL crawl...")
    try:
        response = requests.get(URL, headers=HEADERS, timeout=15)
        soup = BeautifulSoup(response.text, 'html.parser')
    except:
        return []

    scraped_items = []
    table = soup.find('table', {'class': 'vtable'})
    if not table:
        return []

    table_rows = table.find_all('tr')
    for row in table_rows[:15]:
        cells = row.find_all('td')
        if len(cells) >= 3:
            title_cell = cells[0]
            raw_title = title_cell.text.strip()
            apply_url = title_cell.find('a')['href'] if title_cell.find('a') else None
            deadline = cells[2].text.strip()
            post_date = cells[1].text.strip() if len(cells) > 1 else ""

            if apply_url and any(kwd in raw_title for kwd in ["Notification", "Recruitment", "Jobs", "Apply", "Online"]):
                print(f"Deep scraping: {raw_title[:30]}...")
                details = get_job_details(apply_url)

                cat = "Central Govt"
                if "Bank" in raw_title or "IBPS" in raw_title: cat = "Banking"
                elif "SSC" in raw_title: cat = "SSC"
                elif "Railway" in raw_title or "RRB" in raw_title: cat = "Railways"

                item_node = {
                    "title": raw_title,
                    "category": cat,
                    "vacancies": cells[2].text.strip() if len(cells) > 2 else "Check Details",
                    "last_date": deadline if len(deadline) > 2 else "TBA",
                    "district": "All India",
                    "age_limit": details.get("age_limit", ""),
                    "qualification": details.get("qualification", ""),
                    "pattern_table": details.get("pattern_table", ""),
                    "instructions": details.get("instructions", ""),
                    "links": details.get("links", ""),
                    "application_fee": details.get("application_fee", ""),
                    "selection_process": details.get("selection_process", ""),
                    "post_date": post_date,
                    "job_description": details.get("job_description", "")
                }
                scraped_items.append(item_node)
                time.sleep(1)

    return scraped_items

if __name__ == "__main__":
    job_list = scrape_jobs()
    if job_list:
        with open('jobs.jsonl', 'w', encoding='utf-8') as f:
            for item in job_list:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")
        print(f"jobs.jsonl refreshed with {len(job_list)} positions.")
