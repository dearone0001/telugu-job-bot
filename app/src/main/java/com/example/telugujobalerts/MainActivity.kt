package com.example.telugujobalerts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import android.util.Log
import org.json.JSONObject

data class StructuredJobModel(
    val title: String, val category: String, val vacancies: String,
    val lastDate: String, val district: String,
    val ageLimit: String, val qualification: String,
    val patternTable: String, val instructions: String, val links: String,
    val applicationFee: String = "", val selectionProcess: String = "", val postDate: String = "",
    val jobDescription: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Mesh Gradient Background
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFE0EAFC), Color(0xFFCFDEF3))
                            )
                        )
                    )
                    
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                        JobDashboardScreen(activity = this@MainActivity)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDashboardScreen(activity: ComponentActivity) {
    val context = LocalContext.current
    val listState = remember { mutableStateListOf<StructuredJobModel>() }
    var isFetchingData by remember { mutableStateOf(false) }
    var selectedJob by remember { mutableStateOf<StructuredJobModel?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }

    val dataEndpoint = "https://raw.githubusercontent.com/dearone0001/telugu-job-bot/main/jobs.jsonl"

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
        
        fun parseJsonl(jsonlData: String) {
            listState.clear()
            val lines = jsonlData.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                try {
                    val json = JSONObject(trimmed)
                    listState.add(
                        StructuredJobModel(
                            title = json.optString("title"),
                            category = json.optString("category"),
                            vacancies = json.optString("vacancies"),
                            lastDate = json.optString("last_date"),
                            district = json.optString("district"),
                            ageLimit = json.optString("age_limit"),
                            qualification = json.optString("qualification"),
                            patternTable = json.optString("pattern_table"),
                            instructions = json.optString("instructions"),
                            links = json.optString("links"),
                            applicationFee = json.optString("application_fee"),
                            selectionProcess = json.optString("selection_process"),
                            postDate = json.optString("post_date"),
                            jobDescription = json.optString("job_description")
                        )
                    )
                } catch (e: Exception) {
                    Log.e("TeluguJobAlerts", "Error parsing job line", e)
                }
            }
            isFetchingData = false
        }

        fun loadLocalData() {
            try {
                val inputStream = activity.assets.open("jobs.jsonl")
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                parseJsonl(String(buffer))
            } catch (e: Exception) {
                isFetchingData = false
            }
        }

        val request = StringRequest(Request.Method.GET, dataEndpoint,
            { response -> parseJsonl(response) },
            { loadLocalData() }
        )
        queue.add(request)
    }

    LaunchedEffect(Unit) { 
        loadData()
        loadInterstitial()
    }

    if (selectedJob != null) {
        BackHandler { selectedJob = null }
    }

    val filtered = listState.filter { job ->
        val matchesSearch = job.title.contains(searchText, ignoreCase = true) || 
                          job.category.contains(searchText, ignoreCase = true)
        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Andhra Pradesh" -> job.category.contains("Andhra", ignoreCase = true) || 
                                job.district.contains("Andhra", ignoreCase = true) ||
                                job.title.contains("AP ", ignoreCase = true) ||
                                job.title.contains("APPSC", ignoreCase = true)
            "Telangana" -> job.category.contains("Telangana", ignoreCase = true) || 
                           job.district.contains("Telangana", ignoreCase = true) ||
                           job.title.contains("TS ", ignoreCase = true) ||
                           job.title.contains("TSPSC", ignoreCase = true)
            else -> job.category.contains(selectedCategory, ignoreCase = true)
        }
        
        // Client-side expiry check for safety
        val isNotExpired = try {
            val dateStr = job.lastDate.trim()
            if (dateStr.isNotEmpty() && !dateStr.contains("TBA", true)) {
                val parts = if (dateStr.contains("-")) dateStr.split("-") else dateStr.split("/")
                if (parts.size == 3) {
                    val (y, m, d) = if (parts[0].length == 4) {
                        // YYYY-MM-DD
                        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].split(" ")[0].toInt())
                    } else {
                        // DD-MM-YYYY
                        Triple(parts[2].split(" ")[0].toInt(), parts[1].toInt(), parts[0].toInt())
                    }
                    val expiry = java.util.Calendar.getInstance().apply { 
                        set(java.util.Calendar.YEAR, y)
                        set(java.util.Calendar.MONTH, m - 1)
                        set(java.util.Calendar.DAY_OF_MONTH, d)
                        set(java.util.Calendar.HOUR_OF_DAY, 23)
                        set(java.util.Calendar.MINUTE, 59)
                    }
                    expiry.timeInMillis >= System.currentTimeMillis()
                } else true
            } else true
        } catch (e: Exception) { 
            Log.e("TeluguJobAlerts", "Date parse error: ${job.lastDate}", e)
            true 
        }

        matchesSearch && matchesCategory && isNotExpired
    }.sortedByDescending { it.postDate }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (selectedJob == null) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Telugu Job Alerts",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF1A1C1E)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White.copy(alpha = 0.5f)),
                    actions = {
                        IconButton(onClick = { showInterstitial { loadData() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF1A1C1E))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Job Details", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { selectedJob = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White.copy(alpha = 0.5f))
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight().background(Color.White.copy(alpha = 0.5f)).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                AndroidView(modifier = Modifier.fillMaxWidth(), factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        setAdUnitId(AdConfig.BANNER_AD_UNIT_ID)
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.e("AdMob", "Banner failed: ${error.message} (Code: ${error.code})")
                            }
                            override fun onAdLoaded() {
                                Log.d("AdMob", "Banner loaded successfully")
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                })
            }
        }
    ) { padding ->
        val activeJob = selectedJob
        if (activeJob == null) {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Search Bar
                item {
                    Column {
                        Text(
                            text = "Last updated: Just now",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        OutlinedTextField(
                            value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search jobs, companies...", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFF007BF5)) },
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.6f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.4f),
                            focusedIndicatorColor = Color.White,
                            unfocusedIndicatorColor = Color.White.copy(alpha = 0.5f),
                            cursorColor = Color(0xFF007BF5)
                        )
                    )
                }
            }

                // Top Matches for You Section
                item {
                    Text("Top matches for you", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1A1C1E))
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(filtered.reversed().take(5)) { job ->
                            FeaturedJobCard(job = job, onClick = { selectedJob = job })
                        }
                    }
                }

                // Category Row
                item {
                    Text("Categories", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFF1A1C1E))
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val categories = listOf(
                            "All" to Icons.Default.AllInclusive,
                            "Andhra Pradesh" to Icons.Default.LocationOn,
                            "Telangana" to Icons.Default.LocationOn,
                            "Banking" to Icons.Default.AccountBalance,
                            "Railways" to Icons.Default.Train,
                            "SSC" to Icons.AutoMirrored.Filled.Assignment
                        )
                        items(categories) { (name, icon) ->
                            GlassyCategoryItem(
                                name = name, 
                                icon = icon, 
                                isSelected = selectedCategory == name,
                                onClick = { selectedCategory = name }
                            )
                        }
                    }
                }

                // Latest Jobs
                item {
                    Text(
                        if (selectedCategory == "All") "Latest Opportunities" else "$selectedCategory Jobs", 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 20.sp,
                        color = Color(0xFF1A1C1E)
                    )
                }
                
                items(filtered.take(15)) { job ->
                    GlassyJobCard(job = job, onClick = { selectedJob = job })
                }
            }
        } else {
            // Glassy Details Layout
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.8f))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = activeJob.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1C1E), lineHeight = 32.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category = activeJob.category)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "• ${activeJob.district}", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
                if (activeJob.postDate.isNotEmpty()) {
                    Text(text = "Posted on: ${activeJob.postDate}", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                GlassySection(title = "Job Description", content = activeJob.jobDescription)
                GlassySection(title = "Application Fee", content = activeJob.applicationFee)
                GlassySection(title = "Age Limit", content = activeJob.ageLimit)
                GlassySection(title = "Educational Qualification", content = activeJob.qualification)
                GlassySection(title = "Selection Process", content = activeJob.selectionProcess)
                
                if (activeJob.patternTable.isNotEmpty()) {
                    Text(text = "Written Examination Pattern", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007BF5))
                    Spacer(modifier = Modifier.height(12.dp))
                    RenderDataGridTable(tableData = activeJob.patternTable)
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                GlassySection(title = "Important Instructions", content = activeJob.instructions)
                
                Text(text = "Important Links", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007BF5))
                Spacer(modifier = Modifier.height(12.dp))
                
                val linksList = activeJob.links.split("|").filter { it.contains(":") }
                if (linksList.isEmpty()) {
                    Text(text = "Please check the official website for full notification and application details.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                } else {
                    linksList.forEach { linkPart ->
                        val parts = linkPart.split(":")
                        val label = parts[0].trim()
                        val url = linkPart.substring(linkPart.indexOf(":") + 1).trim()
                        
                        if (label.isNotEmpty() && url.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("TeluguJobAlerts", "Error opening link: $url", e)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.4f))
                            ) {
                                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (label.contains("PDF", ignoreCase = true) || label.contains("Notification", ignoreCase = true)) 
                                            Icons.Default.PictureAsPdf else Icons.Default.Link,
                                        contentDescription = null,
                                        tint = Color(0xFF007BF5)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = label, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C1E))
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(text = "Open", color = Color(0xFF007BF5), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out this job: ${activeJob.title}\nDetails: ${activeJob.qualification}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BF5))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Job with Friends", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun FeaturedJobCard(job: StructuredJobModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0F2FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Work, contentDescription = null, tint = Color(0xFF007BF5), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = job.category, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF007BF5))
                        Text(text = job.district, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = job.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFF1A1C1E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GlassyCategoryItem(name: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isSelected) Color(0xFF007BF5) else Color.White.copy(alpha = 0.4f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = if (isSelected) Color.White else Color(0xFF007BF5),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            name, 
            fontSize = 13.sp, 
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) Color(0xFF007BF5) else Color(0xFF1A1C1E)
        )
    }
}

@Composable
fun GlassyJobCard(job: StructuredJobModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFE0F2FE).copy(alpha = 0.8f), Color(0xFFF1F5F9).copy(alpha = 0.8f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        job.category.contains("Bank", true) -> Icons.Default.AccountBalance
                        job.category.contains("SSC", true) -> Icons.Default.Work
                        else -> Icons.Default.Business
                    },
                    contentDescription = null,
                    tint = Color(0xFF007BF5),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1A1C1E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = job.category, color = Color.Gray, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "• ${job.district}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Expires: ${job.lastDate}", color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF007BF5))
                }
            }
        }
    }
}

@Composable
fun GlassySection(title: String, content: String) {
    if (content.isEmpty()) return
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF007BF5))
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content.split("\n").forEach { line ->
                    if (line.trim().isNotEmpty()) {
                        Text(
                            text = line, 
                            fontSize = 16.sp, 
                            color = Color(0xFF263238), 
                            lineHeight = 26.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryBadge(category: String) {
    Surface(
        color = Color(0xFFE0F2FE).copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF007BF5),
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun RenderDataGridTable(tableData: String) {
    val rows = tableData.split("\n").filter { it.isNotBlank() }
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(1.dp)) {
            rows.forEachIndexed { index, rowText ->
                val cells = rowText.split("|")
                val isHeader = index == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isHeader) Color(0xFF007BF5).copy(alpha = 0.1f) else Color.Transparent)
                ) {
                    cells.forEach { cellText ->
                        Text(
                            text = cellText.trim(),
                            modifier = Modifier.weight(1f).padding(12.dp),
                            fontSize = 14.sp,
                            fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            color = if (isHeader) Color(0xFF007BF5) else Color.DarkGray
                        )
                    }
                }
                if (index < rows.size - 1) HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
            }
        }
    }
}
