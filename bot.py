import requests
from bs4 import BeautifulSoup
import csv
import json
import time

def get_job_details(url):
    headers = {'User-Agent': 'Mozilla/5.0'}
    try:
        response = requests.get(url, headers=headers, timeout=10)
        if response.status_code != 200:
            return {}

        soup = BeautifulSoup(response.text, 'html.parser')
        content = soup.find('div', {'class': 'post-content'}) or soup.find('article')
        if not content:
            return {}

        details = {
            "age_limit": "",
            "qualification": "",
            "pattern_table": "",
            "instructions": "",
            "links": ""
        }

        # Extract Age Limit
        age_section = content.find(lambda t: t.name in ['h2', 'h3', 'p', 'strong'] and "Age Limit" in t.text)
        if age_section:
            details["age_limit"] = age_section.find_next('p').text.strip() if age_section.find_next('p') else ""

        # Extract Qualification
        qual_section = content.find(lambda t: t.name in ['h2', 'h3', 'p', 'strong'] and "Qualification" in t.text)
        if qual_section:
            details["qualification"] = qual_section.find_next('p').text.strip() if qual_section.find_next('p') else ""

        # Extract Pattern Table
        pattern_table = content.find('table')
        if pattern_table:
            rows_list = []
            for row in pattern_table.find_all('tr'):
                cells = [c.text.strip() for c in row.find_all(['td', 'th'])]
                rows_list.append("|".join(cells))
            details["pattern_table"] = "\n".join(rows_list)

        # Extract Links
        links_list = []
        target_links = ["Apply Online", "Official Notification", "Official Website"]
        for a in content.find_all('a'):
            for target in target_links:
                if target.lower() in a.text.lower():
                    links_list.append(f"{target}:{a['href']}")
                    break
        details["links"] = "|".join(links_list)

        return details

    except Exception:
        return {}

def scrape_jobs():
    print("Starting exact app layout scraper...")
    sources = [
        {"url": "https://www.freejobalert.com/andhra-pradesh-govt-jobs/", "category": "AP Govt", "district": "Andhra Pradesh"},
        {"url": "https://www.freejobalert.com/telangana-govt-jobs/", "category": "TS Govt", "district": "Telangana"}
    ]

    headers = {'User-Agent': 'Mozilla/5.0'}
    jobs_data = []

    for source in sources:
        try:
            response = requests.get(source['url'], headers=headers, timeout=10)
            soup = BeautifulSoup(response.text, 'html.parser')
            table = soup.find('table', {'class': 'vtable'})
            if not table: continue

            rows = table.find_all('tr')[1:6]
            for row in rows:
                cols = row.find_all('td')
                if len(cols) >= 3:
                    title_cell = cols[0]
                    title_text = title_cell.text.strip()
                    if not title_text or "Post Date" in title_text: continue

                    vacancies = cols[2].text.strip()
                    last_date = cols[1].text.strip()

                    link_tag = title_cell.find('a')
                    if link_tag:
                        detail_url = link_tag.get('href', '')
                        details = get_job_details(detail_url)

                        # Pack complex details into JSON to fit CSV column
                        # Use '~~' to replace commas to avoid CSV breakage
                        json_str = json.dumps(details).replace(",", "~~")

                        jobs_data.append([
                            title_text, source['category'], vacancies,
                            last_date, source['district'], json_str
                        ])
                        time.sleep(1)

        except Exception as e:
            print(f"Error: {e}")

    with open('jobs.csv', mode='w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['Title', 'Category', 'Vacancies', 'LastDate', 'District', 'StructuredDetails'])
        writer.writerows(jobs_data)

if __name__ == "__main__":
    scrape_jobs()
