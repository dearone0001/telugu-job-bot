package com.example.telugujobalerts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
    val patternTable: String, val instructions: String, val links: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F9FC)) {
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
    val listState = remember { mutableStateListOf<StructuredJobModel>() }
    var isFetchingData by remember { mutableStateOf(false) }
    var selectedJob by remember { mutableStateOf<StructuredJobModel?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedNavIndex by remember { mutableIntStateOf(0) }
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
                            links = json.optString("links")
                        )
                    )
                } catch (e: Exception) {}
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
        val matchesCategory = if (selectedCategory == "All") true 
                            else job.category.contains(selectedCategory, ignoreCase = true)
        matchesSearch && matchesCategory
    }

    Scaffold(
        topBar = {
            if (selectedJob == null) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Telugu Job Alerts",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
                    actions = {
                        IconButton(onClick = { showInterstitial { loadData() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Job Details", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { selectedJob = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        bottomBar = {
            Column {
                Box(modifier = Modifier.fillMaxWidth().wrapContentHeight().background(Color.White).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
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
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    NavigationBarItem(
                        selected = selectedNavIndex == 0,
                        onClick = { selectedNavIndex = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedNavIndex == 1,
                        onClick = { selectedNavIndex = 1 },
                        icon = { Icon(Icons.Default.Work, contentDescription = "Jobs") },
                        label = { Text("Jobs") }
                    )
                    NavigationBarItem(
                        selected = selectedNavIndex == 2,
                        onClick = { selectedNavIndex = 2 },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts") },
                        label = { Text("Alerts") }
                    )
                    NavigationBarItem(
                        selected = selectedNavIndex == 3,
                        onClick = { selectedNavIndex = 3 },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile") }
                    )
                }
            }
        }
    ) { padding ->
        val activeJob = selectedJob
        if (activeJob == null) {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color(0xFFF7F9FC)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section 1: Search Bar
                item {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search jobs, companies...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color(0xFF007BF5),
                            unfocusedIndicatorColor = Color.LightGray
                        )
                    )
                }

                // Section 2: Category Row
                item {
                    Text("Categories", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val categories = listOf(
                            "All" to Icons.Default.AllInclusive,
                            "Central" to Icons.Default.Public,
                            "Banking" to Icons.Default.AccountBalance,
                            "Railways" to Icons.Default.Train,
                            "SSC" to Icons.Default.Assignment,
                            "State" to Icons.Default.LocationCity
                        )
                        items(categories) { (name, icon) ->
                            CategoryItem(
                                name = name, 
                                icon = icon, 
                                isSelected = selectedCategory == name,
                                onClick = { selectedCategory = name }
                            )
                        }
                    }
                }

                // Section 3: Latest Jobs
                item {
                    Text(
                        if (selectedCategory == "All") "Latest Jobs" else "$selectedCategory Jobs", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 18.sp
                    )
                }
                
                items(filtered.take(15)) { job ->
                    ModernJobCard(job = job, onClick = { selectedJob = job })
                }
            }
        } else {
            // High-end Details Layout
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = activeJob.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1C1E))
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category = activeJob.category)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "• ${activeJob.district}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(24.dp))
                RenderTextSection(title = "Age Limit", content = activeJob.ageLimit)
                RenderTextSection(title = "Educational Qualification", content = activeJob.qualification)
                if (activeJob.patternTable.isNotEmpty()) {
                    Text(text = "Written Examination Pattern", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007BF5))
                    Spacer(modifier = Modifier.height(12.dp))
                    RenderDataGridTable(tableData = activeJob.patternTable)
                    Spacer(modifier = Modifier.height(24.dp))
                }
                RenderTextSection(title = "Important Instructions", content = activeJob.instructions)
                Text(text = "Important Links", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007BF5))
                Spacer(modifier = Modifier.height(12.dp))
                activeJob.links.split("|").forEach { linkPart ->
                    val parts = linkPart.split(":")
                    val label = parts[0].trim()
                    val url = if (parts.size > 1) linkPart.substring(linkPart.indexOf(":") + 1).trim() else ""
                    if (label.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                if (url.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF007BF5))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = label, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C1E))
                                Spacer(modifier = Modifier.weight(1f))
                                Text(text = "Visit", color = Color(0xFF007BF5), fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BF5))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Job with Friends", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun CategoryItem(name: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF007BF5) else Color.White)
                .border(1.dp, if (isSelected) Color(0xFF007BF5) else Color(0xFFE0E0E0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = if (isSelected) Color.White else Color(0xFF007BF5)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            name, 
            fontSize = 12.sp, 
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color(0xFF007BF5) else Color.Black
        )
    }
}

@Composable
fun ModernJobCard(job: StructuredJobModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFE0F2FE), Color(0xFFF1F5F9)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        job.category.contains("Bank", true) -> Icons.Default.AccountBalance
                        job.category.contains("SSC", true) -> Icons.Default.Work
                        else -> Icons.Default.Business
                    },
                    contentDescription = null,
                    tint = Color(0xFF007BF5)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1C1E),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = job.category, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "• ${job.district}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Last Date: ${job.lastDate}", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF007BF5))
                }
            }
        }
    }
}

@Composable
fun CategoryBadge(category: String) {
    Surface(
        color = Color(0xFFE0F2FE),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF007BF5),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RenderTextSection(title: String, content: String) {
    if (content.isEmpty()) return
    Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007BF5))
    Spacer(modifier = Modifier.height(8.dp))
    content.split("\n").forEach { line ->
        if (line.trim().isNotEmpty()) {
            Text(
                text = line, 
                fontSize = 15.sp, 
                color = Color(0xFF263238), 
                lineHeight = 24.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
fun RenderDataGridTable(tableData: String) {
    val rows = tableData.split("\n").filter { it.isNotBlank() }
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFCFD8DC))) {
        rows.forEachIndexed { index, rowText ->
            val cells = rowText.split("|")
            val isHeader = index == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isHeader) Color(0xFFE1F5FE) else Color.White)
                    .border(0.5.dp, Color(0xFFCFD8DC))
            ) {
                cells.forEach { cellText ->
                    Text(
                        text = cellText.trim(),
                        modifier = Modifier.weight(1f).padding(8.dp),
                        fontSize = 13.sp,
                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
