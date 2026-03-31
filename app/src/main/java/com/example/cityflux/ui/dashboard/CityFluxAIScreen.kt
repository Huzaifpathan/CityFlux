package com.example.cityflux.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.R
import com.example.cityflux.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * CityFlux AI Assistant Screen
 * Powered by Groq API (Free, Fast Llama 3)
 * 
 * SETUP INSTRUCTIONS:
 * 1. Get your FREE API key from: https://console.groq.com/keys
 * 2. Add to local.properties: GROQ_API_KEY=your_api_key_here
 * 3. Sync Gradle and rebuild
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityFluxAIScreen(
    onClose: () -> Unit,
    apiKey: String,
    userType: String = "citizen" // "citizen" or "police"
) {
    val colors = MaterialTheme.cityFluxColors
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // Initialize OkHttp client for Groq API
    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // System prompt based on user type
    val systemContext = remember(userType) {
        if (userType == "police") {
            """You are CityFlux AI, an intelligent assistant for police officers in a smart city traffic management system.
            |You help with:
            |• Traffic violation procedures and regulations
            |• Congestion management strategies
            |• Emergency response protocols
            |• Report filing assistance
            |• Parking enforcement guidelines
            |• Citizen complaint handling
            |• Route optimization for patrol
            |Keep responses professional, concise, and actionable.
            |Always prioritize public safety in your recommendations.""".trimMargin()
        } else {
            """You are CityFlux AI, a friendly assistant for citizens using the CityFlux smart city app.
            |You help with:
            |• Reporting traffic issues, potholes, accidents
            |• Finding nearby parking spots
            |• Understanding traffic rules and regulations
            |• Navigating the city efficiently
            |• Tracking complaint status
            |• Understanding traffic fines and payments
            |• Community safety tips
            |Be helpful, friendly, and provide practical advice.
            |Keep responses easy to understand for all users.""".trimMargin()
        }
    }
    
    // Add welcome message on first load
    LaunchedEffect(Unit) {
        val welcomeMessage = if (userType == "police") {
            "Hello Officer! 👮 I'm CityFlux AI, your intelligent assistant powered by Llama 3. How can I help you with traffic management, reports, or city operations today?"
        } else {
            "Hi there! 👋 I'm CityFlux AI, your smart city assistant powered by Llama 3. I can help you with traffic reports, finding parking, understanding regulations, or anything else about navigating our city. What would you like to know?"
        }
        messages = listOf(ChatMessage(welcomeMessage, isFromUser = false))
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Function to call Groq API
    suspend fun callGroqApi(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemContext)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                }
                
                val requestBody = JSONObject().apply {
                    put("model", "llama-3.3-70b-versatile")
                    put("messages", messagesArray)
                    put("temperature", 0.7)
                    put("max_tokens", 1024)
                    put("top_p", 0.95)
                }.toString()
                
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val choices = jsonResponse.getJSONArray("choices")
                    if (choices.length() > 0) {
                        choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                    } else {
                        "I apologize, but I couldn't generate a response. Please try again."
                    }
                } else {
                    val errorMsg = try {
                        JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: "Unknown error"
                    } catch (e: Exception) {
                        responseBody
                    }
                    "API Error: $errorMsg"
                }
            } catch (e: Exception) {
                "Sorry, I encountered an error: ${e.localizedMessage}. Please check your connection and try again."
            }
        }
    }
    
    fun sendMessage() {
        if (inputText.isBlank() || isLoading) return
        
        val userMessage = inputText.trim()
        messages = messages + ChatMessage(userMessage, isFromUser = true)
        inputText = ""
        isLoading = true
        focusManager.clearFocus()
        
        coroutineScope.launch {
            val aiResponse = callGroqApi(userMessage)
            messages = messages + ChatMessage(aiResponse, isFromUser = false)
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ai_assistant),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "CityFlux AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                "Smart City Assistant",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { 
                            Text(
                                "Ask CityFlux AI...",
                                color = colors.textSecondary
                            ) 
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.inputBorderFocused,
                            unfocusedBorderColor = colors.inputBorder
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                        maxLines = 4,
                        enabled = !isLoading
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FilledIconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.isNotBlank() && !isLoading,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = PrimaryBlue,
                            contentColor = Color.White
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send"
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message, colors = colors)
            }
            
            // Loading indicator
            if (isLoading) {
                item {
                    TypingIndicator(colors = colors)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    colors: CityFluxColors
) {
    val bubbleColor = if (message.isFromUser) {
        PrimaryBlue
    } else {
        if (colors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    }
    
    val textColor = if (message.isFromUser) {
        Color.White
    } else {
        colors.textPrimary
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isFromUser) {
            // AI Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "AI",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun TypingIndicator(colors: CityFluxColors) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "AI",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (colors.isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(dot1Alpha, dot2Alpha, dot3Alpha).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}
