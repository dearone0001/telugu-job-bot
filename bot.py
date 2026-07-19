import requests
from bs4 import BeautifulSoup
import csv
import os
import time

def get_job_details(url):
    """Fetch and parse comprehensive details from an individual job page."""
    headers = {'User-Agent': 'Mozilla/5.0'}
    try:
        print(f"Fetching full details from: {url}")
        response = requests.get(url, headers=headers, timeout=10)
        if response.status_code != 200:
            return "మరిన్ని వివరాలు నోటిఫికేషన్లో చూడండి"

        soup = BeautifulSoup(response.text, 'html.parser')

        # FreeJobAlert usually puts main content in a specific div
        # We'll extract common sections like Qualification, Age Limit, Fee, etc.
        content = soup.find('div', {'class': 'post-content'}) or soup.find('article')
        if not content:
            return "వివరాలు అందుబాటులో లేవు. దయచేసి వెబ్సైట్ చూడండి."

        extracted_text = []

        # Look for headers and their subsequent content
        targets = ["Age Limit", "Qualification", "Application Fee", "Vacancy Details", "Important Dates"]

        # This is a simplified extraction. In a real production app,
        # you'd use a more robust parser for different table structures.
        for tag in content.find_all(['h2', 'h3', 'p', 'table', 'li']):
            text = tag.text.strip()
            if any(t in text for t in targets):
                extracted_text.append(f"\n--- {text} ---\n")
            elif tag.name == 'li':
                extracted_text.append(f"• {text}")
            elif tag.name == 'table':
                # Simplified table to text conversion
                for row in tag.find_all('tr'):
                    cells = [c.text.strip() for c in row.find_all(['td', 'th'])]
                    extracted_text.append(" | ".join(cells))
            else:
                if len(text) > 10: # Avoid noise
                    extracted_text.append(text)

        # Join with newlines and clean up
        full_details = "\n".join(extracted_text[:20]) # Limit length for CSV
        return full_details if full_details.strip() else "వివరాల కోసం కింద ఉన్న బటన్ నొక్కండి."

    except Exception as e:
        print(f"Error fetching details: {e}")
        return "వివరాలు లోడ్ చేయడంలో లోపం కలిగింది."

def scrape_jobs():
    print("Starting Comprehensive Job Scraper...")

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

            rows = table.find_all('tr')[1:6] # Fetch fewer jobs but deeper details for each

            for row in rows:
                cols = row.find_all('td')
                if len(cols) >= 3:
                    title_cell = cols[0]
                    title_text = title_cell.text.strip()
                    if not title_text or "Post Date" in title_text: continue

                    vacancies = cols[2].text.strip()
                    last_date = cols[1].text.strip()

                    link_tag = title_cell.find('a')
                    apply_link = link_tag['href'] if link_tag and 'href' in link_tag.attrs else source['url']

                    # Fetch deep details from the individual post page
                    # WARNING: This makes the scraper slower but satisfies the user's request
                    deep_details = get_job_details(apply_link)

                    jobs_data.append({
                        'Title': f"{title_text} - తాజా అప్డేట్",
                        'Category': source['category'],
                        'Vacancies': vacancies,
                        'LastDate': last_date,
                        'District': source['district'],
                        'ApplyLink': apply_link,
                        'Details': deep_details
                    })
                    time.sleep(1) # Be nice to server

        except Exception as e:
            print(f"Error scraping {source['category']}: {e}")

    fieldnames = ['Title', 'Category', 'Vacancies', 'LastDate', 'District', 'ApplyLink', 'Details']
    with open('jobs.csv', mode='w', newline='', encoding='utf-8') as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(jobs_data)

    print(f"Successfully scraped {len(jobs_data)} jobs with full details.")

if __name__ == "__main__":
    scrape_jobs()
