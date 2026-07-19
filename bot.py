import requests
from bs4 import BeautifulSoup
import csv
import os
import time

def get_job_details(url):
    """Fetch and parse comprehensive details from an individual job page."""
    headers = {'User-Agent': 'Mozilla/5.0'}
    try:
        print(f"Fetching full details and direct apply link from: {url}")
        response = requests.get(url, headers=headers, timeout=10)
        if response.status_code != 200:
            return "మరిన్ని వివరాలు నోటిఫికేషన్లో చూడండి", url

        soup = BeautifulSoup(response.text, 'html.parser')

        # 1. FIND DIRECT APPLY LINK (Targeting official websites)
        direct_apply_link = url # Default to detail page
        # FreeJobAlert usually has a table with "Apply Online" or "Official Website" links
        link_table = soup.find('table', {'class': 'vtable'}) or soup.find('table')
        if link_table:
            for link in link_table.find_all('a'):
                link_text = link.text.lower()
                if "click here" in link_text or "apply online" in link_text or "official website" in link_text:
                    href = link.get('href', '')
                    # We want links that DON'T lead back to freejobalert.com if possible
                    if href and "freejobalert" not in href:
                        direct_apply_link = href
                        break

        # 2. EXTRACT DETAILS
        content = soup.find('div', {'class': 'post-content'}) or soup.find('article')
        if not content:
            return "వివరాలు అందుబాటులో లేవు. దయచేసి వెబ్సైట్ చూడండి.", direct_apply_link

        extracted_text = []
        targets = ["Age Limit", "Qualification", "Application Fee", "Vacancy Details", "Important Dates"]

        for tag in content.find_all(['h2', 'h3', 'p', 'table', 'li']):
            text = tag.text.strip()
            if any(t in text for t in targets):
                extracted_text.append(f"\n--- {text} ---\n")
            elif tag.name == 'li':
                extracted_text.append(f"• {text}")
            elif tag.name == 'table':
                for row in tag.find_all('tr'):
                    cells = [c.text.strip() for c in row.find_all(['td', 'th'])]
                    extracted_text.append(" | ".join(cells))
            else:
                if len(text) > 10:
                    extracted_text.append(text)

        full_details = "\n".join(extracted_text[:20])
        return full_details if full_details.strip() else "వివరాల కోసం కింద ఉన్న బటన్ నొక్కండి.", direct_apply_link

    except Exception as e:
        print(f"Error fetching details: {e}")
        return "వివరాలు లోడ్ చేయడంలో లోపం కలిగింది.", url

def scrape_jobs():
    print("Starting Direct-Link Job Scraper...")

    sources = [
        {"url": "https://www.freejobalert.com/andhra-pradesh-govt-jobs/", "category": "AP Govt", "district": "Andhra Pradesh"},
        {"url": "https://www.freejobalert.com/telangana-govt-jobs/", "category": "TS Govt", "district": "Telangana"},
        {"url": "https://www.freejobalert.com/government-jobs/", "category": "Central Govt", "district": "India"},
        {"url": "https://www.freejobalert.com/bank-jobs/", "category": "Banking", "district": "India"}
    ]

    headers = {'User-Agent': 'Mozilla/5.0'}
    jobs_data = []

    for source in sources:
        try:
            print(f"Scraping category: {source['category']}...")
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

                    detail_page_url = ""
                    link_tag = title_cell.find('a')
                    if link_tag:
                        detail_page_url = link_tag.get('href', '')

                    if detail_page_url:
                        # Fetch deep details AND the direct application link
                        deep_details, direct_link = get_job_details(detail_page_url)

                        jobs_data.append({
                            'Title': f"{title_text} - తాజా అప్డేట్",
                            'Category': source['category'],
                            'Vacancies': vacancies,
                            'LastDate': last_date,
                            'District': source['district'],
                            'ApplyLink': direct_link, # NOW SAVING DIRECT GOVT LINK
                            'Details': deep_details
                        })
                        time.sleep(1)

        except Exception as e:
            print(f"Error scraping {source['category']}: {e}")

    fieldnames = ['Title', 'Category', 'Vacancies', 'LastDate', 'District', 'ApplyLink', 'Details']
    with open('jobs.csv', mode='w', newline='', encoding='utf-8') as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(jobs_data)

    print(f"Successfully scraped {len(jobs_data)} jobs with direct apply links.")

if __name__ == "__main__":
    scrape_jobs()
