package com.example.telugujobalerts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

data class JobModel(
    val title: String, val category: String, val vacancies: String,
    val lastDate: String, val district: String, val applyLink: String,
    val details: String = ""
)
val dataEndpoint = "https://raw.githubusercontent.com/dearone0001/telugu-job-bot/main/jobs.csv"
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JobDashboardScreen(activity = this)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDashboardScreen(activity: ComponentActivity) {
    val context = LocalContext.current
    val listState = remember { mutableStateListOf<JobModel>() }
    var isFetchingData by remember { mutableStateOf(false) }
    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedJob by remember { mutableStateOf<JobModel?>(null) }
    val tabs = listOf("All India", "AP/TS", "Banking", "SSC/RRB")

    fun loadInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, AdConfig.INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }
            })
    }

    fun showInterstitial(onAdDismissed: () -> Unit) {
        if (interstitialAd != null) {
            interstitialAd?.show(activity)
            loadInterstitial()
            onAdDismissed()
        } else {
            onAdDismissed()
        }
    }

    fun loadData() {
        isFetchingData = true
        val queue = Volley.newRequestQueue(activity)
        
        fun parseCsv(csvData: String) {
            listState.clear()
            val entries = csvData.split("\n")
            for (i in 1 until entries.size) {
                val line = entries[i].trim()
                if (line.isEmpty()) continue
                val cells = line.split(",")
                if (cells.size >= 6) {
                    val details = if (cells.size >= 7) cells[6].trim() else "మరిన్ని వివరాలు నోటిఫికేషన్లో చూడండి"
                    listState.add(JobModel(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5].trim(), details))
                }
            }
            isFetchingData = false
        }

        fun loadLocalData() {
            try {
                val inputStream = activity.assets.open("jobs.csv")
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                parseCsv(String(buffer))
            } catch (e: Exception) {
                isFetchingData = false
            }
        }

        val request = StringRequest(Request.Method.GET, dataEndpoint,
            { response -> parseCsv(response) },
            { loadLocalData() }
        )
        queue.add(request)
    }

    LaunchedEffect(Unit) {
        loadData()
        loadInterstitial()
    }

    val filteredJobs = when (selectedTab) {
        0 -> listState.filter { it.category.contains("Central", true) || it.district.contains("India", true) }
        1 -> listState.filter { it.category.contains("AP", true) || it.category.contains("TS", true) || it.district.contains("Pradesh", true) || it.district.contains("Telangana", true) }
        2 -> listState.filter { it.category.contains("Bank", true) }
        3 -> listState.filter { it.category.contains("SSC", true) || it.category.contains("Railway", true) || it.category.contains("RRB", true) }
        else -> listState
    }

    if (selectedJob != null) {
        JobDetailsScreen(job = selectedJob!!, onBack = { selectedJob = null })
    } else {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "Free Job Alert Telugu",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        actions = {
                            IconButton(onClick = { /* TODO: Search */ }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { showInterstitial { loadData() } }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        edgePadding = 16.dp,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { 
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(modifier = Modifier.fillMaxWidth(), factory = { ctx ->
                            AdView(ctx).apply {
                                setAdSize(AdSize.BANNER)
                                setAdUnitId(AdConfig.BANNER_AD_UNIT_ID)
                                loadAd(AdRequest.Builder().build())
                            }
                        })
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = true,
                            onClick = { },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { },
                            icon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts") },
                            label = { Text("Alerts") }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { },
                            icon = { Icon(Icons.Default.Favorite, contentDescription = "Saved") },
                            label = { Text("Saved") }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
            ) {
                if (filteredJobs.isEmpty() && !isFetchingData) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No jobs found in this category.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredJobs) { job ->
                            FreeJobAlertCard(job, onClick = { selectedJob = job })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailsScreen(job: JobModel, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out this job: ${job.title}\n${job.applyLink}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                text = job.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    DetailRow("Category", job.category)
                    DetailRow("District", job.district)
                    DetailRow("Vacancies", job.vacancies)
                    DetailRow("Last Date", job.lastDate)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Render structured details from scraper
            val detailSections = job.details.split("---")
            detailSections.forEach { section ->
                val lines = section.trim().split("\n")
                if (lines.isNotEmpty()) {
                    val header = lines[0].trim()
                    if (header.isNotEmpty()) {
                        Text(
                            text = header.replace("-", "").trim(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF007BF5),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        lines.drop(1).forEach { line ->
                            if (line.trim().isNotEmpty()) {
                                Text(
                                    text = line.trim(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    lineHeight = 24.sp
                                )
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFEEEEEE))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(job.applyLink))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BF5))
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Apply Online (Official Website)", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Check out this job: ${job.title}\nDetails: ${job.details}\nApply here: ${job.applyLink}")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share This Job", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "$label: ", fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(text = value, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FreeJobAlertCard(job: JobModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    color = when {
                        job.category.contains("AP", true) -> Color(0xFFFFEBEE)
                        job.category.contains("Bank", true) -> Color(0xFFE3F2FD)
                        else -> Color(0xFFF3E5F5)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = job.category,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            job.category.contains("AP", true) -> Color(0xFFC62828)
                            job.category.contains("Bank", true) -> Color(0xFF1565C0)
                            else -> Color(0xFF7B1FA2)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = job.lastDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = job.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Text(text = job.district, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                Text(text = "${job.vacancies} Posts", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            
            if (job.details.isNotEmpty()) {
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFEEEEEE))
                Text(
                    text = job.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Get Details", fontWeight = FontWeight.Bold)
            }
        }
    }
}