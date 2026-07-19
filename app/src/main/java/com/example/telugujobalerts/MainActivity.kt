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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
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

    // Updated data endpoint to use the new JSONL source
    val dataEndpoint = "https://raw.githubusercontent.com/dearone0001/telugu-job-bot/main/jobs.jsonl"

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
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
            isFetchingData = false
        }

        fun loadLocalData() {
            try {
                // Fallback to local JSONL if remote fails
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
    }

    if (selectedJob != null) {
        BackHandler { selectedJob = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = if (selectedJob == null) "Telugu Job Alerts" else "Job Details", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = {
                    if (selectedJob != null) {
                        IconButton(onClick = { selectedJob = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                    IconButton(onClick = { }) { Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Save") }
                    IconButton(onClick = {
                        val shareText = if (selectedJob != null) "Check out this job: ${selectedJob!!.title}\nLast Date: ${selectedJob!!.lastDate}" else "Check out latest Telugu Job Alerts!"
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().wrapContentHeight().background(Color.White), contentAlignment = Alignment.Center) {
                AndroidView(modifier = Modifier.fillMaxWidth(), factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        setAdUnitId(AdConfig.BANNER_AD_UNIT_ID)
                        loadAd(AdRequest.Builder().build())
                    }
                })
            }
        }
    ) { padding ->
        val activeJob = selectedJob
        if (activeJob == null) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    items(listState) { job ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { selectedJob = job },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = job.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Category: ${job.category}", color = Color.Gray)
                                    Text(text = "Last Date: ${job.lastDate}", color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { selectedJob = job },
                                    modifier = Modifier.align(Alignment.End),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                                ) {
                                    Text("Open Details")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = activeJob.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(16.dp))

                RenderTextSection(title = "Age Limit", content = activeJob.ageLimit)
                RenderTextSection(title = "Educational Qualification", content = activeJob.qualification)

                if (activeJob.patternTable.isNotEmpty()) {
                    Text(text = "Written Examination Pattern", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                    Spacer(modifier = Modifier.height(8.dp))
                    RenderDataGridTable(tableData = activeJob.patternTable)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                RenderTextSection(title = "Important Instructions", content = activeJob.instructions)

                Text(text = "Important Links", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                Spacer(modifier = Modifier.height(8.dp))
                activeJob.links.split("|").forEach { linkPart ->
                    val parts = linkPart.split(":")
                    val label = parts[0].trim()
                    val url = if (parts.size > 1) linkPart.substring(linkPart.indexOf(":") + 1).trim() else ""
                    
                    if (label.isNotEmpty()) {
                        Text(text = "• $label: ", fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Click here",
                            fontSize = 15.sp,
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = 16.dp, bottom = 8.dp)
                                .clickable {
                                    if (url.isNotEmpty()) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    }
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out this job: ${activeJob.title}\nDetails: ${activeJob.qualification}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share This Job", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun RenderTextSection(title: String, content: String) {
    if (content.isEmpty()) return
    Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
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
