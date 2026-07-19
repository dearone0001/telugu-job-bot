import requests
from bs4 import BeautifulSoup
import csv
import os
import time

def scrape_jobs():
    print("Starting All India Job Scraper...")

    # List of URLs to scrape for comprehensive Indian job alerts
    sources = [
        {"url": "https://www.freejobalert.com/andhra-pradesh-govt-jobs/", "category": "AP Govt", "district": "Andhra Pradesh"},
        {"url": "https://www.freejobalert.com/telangana-govt-jobs/", "category": "TS Govt", "district": "Telangana"},
        {"url": "https://www.freejobalert.com/government-jobs/", "category": "Central Govt", "district": "India"},
        {"url": "https://www.freejobalert.com/bank-jobs/", "category": "Banking", "district": "India"},
        {"url": "https://www.freejobalert.com/ssc-jobs/", "category": "SSC", "district": "India"},
        {"url": "https://www.freejobalert.com/railway-jobs/", "category": "Railways", "district": "India"}
    ]

    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'}
    jobs_data = []

    for source in sources:
        try:
            print(f"Scraping: {source['url']}...")
            response = requests.get(source['url'], headers=headers, timeout=10)
            if response.status_code != 200:
                print(f"Failed to fetch {source['url']}")
                continue

            soup = BeautifulSoup(response.text, 'html.parser')
            table = soup.find('table', {'class': 'vtable'})

            if not table:
                print(f"No table found for {source['category']}")
                continue

            rows = table.find_all('tr')[1:15] # Get top 14 jobs from each category

            for row in rows:
                cols = row.find_all('td')
                if len(cols) >= 3:
                    title_cell = cols[0]
                    title_text = title_cell.text.strip()

                    # Skip rows that are just headers or empty
                    if not title_text or "Post Date" in title_text:
                        continue

                    vacancies = cols[2].text.strip()
                    last_date = cols[1].text.strip()

                    # Extract the actual post link
                    link_tag = title_cell.find('a')
                    apply_link = link_tag['href'] if link_tag and 'href' in link_tag.attrs else source['url']

                    # Refined Notification Details logic
                    # We create a cleaner detail string for the UI
                    detail_info = f"పోస్టుల సంఖ్య: {vacancies} | చివరి తేదీ: {last_date}"

                    # Add Telugu branding to the title
                    processed_title = f"{title_text} - తాజా ఉద్యోగ సమాచారం"

                    jobs_data.append({
                        'Title': processed_title,
                        'Category': source['category'],
                        'Vacancies': vacancies,
                        'LastDate': last_date,
                        'District': source['district'],
                        'ApplyLink': apply_link,
                        'Details': detail_info
                    })

            # Polite scraping
            time.sleep(1)

        except Exception as e:
            print(f"Error scraping {source['category']}: {e}")

    # Define the exact columns needed for the Android app
    fieldnames = ['Title', 'Category', 'Vacancies', 'LastDate', 'District', 'ApplyLink', 'Details']

    with open('jobs.csv', mode='w', newline='', encoding='utf-8') as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(jobs_data)

    print(f"Successfully scraped total {len(jobs_data)} jobs into jobs.csv")

if __name__ == "__main__":
    scrape_jobs()
