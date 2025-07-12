package com.example.kebbivoicebot

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.*

class MainActivity : ComponentActivity() {
    private var recorder: MediaRecorder? = null
    private var outputFile: String = ""
    private lateinit var tts: TextToSpeech

    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.TAIWAN
            }
        }

        setContent {
            val chatViewModel: ChatViewModel = viewModel()
            val reply by chatViewModel.reply.collectAsState()

            LaunchedEffect(reply) {
                if (reply.isNotBlank()) {
                    speak(reply)
                }
            }

            VoiceChatUI(
                onStartRecording = { startRecording() },
                onStopRecordingAndSend = {
                    stopRecording()
                    uploadAudioAndGetResponse(chatViewModel)
                },
                chatViewModel = chatViewModel
            )
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        if (!hasRecordAudioPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "錄音權限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "錄音權限被拒絕", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        if (!hasRecordAudioPermission()) {
            Toast.makeText(this, "請先開啟錄音權限", Toast.LENGTH_SHORT).show()
            requestPermissions()
            return
        }

        outputFile = "${externalCacheDir?.absolutePath}/audio.wav"
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(outputFile)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            prepare()
            start()
        }
        Toast.makeText(this, "開始錄音", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        Toast.makeText(this, "錄音結束", Toast.LENGTH_SHORT).show()
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun uploadAudioAndGetResponse(viewModel: ChatViewModel) {
        val file = File(outputFile)
        if (!file.exists()) {
            Toast.makeText(this, "錄音檔案不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val requestBody = file.asRequestBody("audio/wav".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Part.createFormData("file", file.name, requestBody)

        RetrofitClient.api.uploadAudio(multipartBody).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful) {
                    val result = response.body()?.reply ?: "（沒有回應）"
                    Toast.makeText(this@MainActivity, "上傳成功", Toast.LENGTH_SHORT).show()
                    viewModel.setReply(result)
                    speak(result)
                } else {
                    Toast.makeText(this@MainActivity, "上傳錯誤: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "上傳失敗: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}

@Composable
fun VoiceChatUI(
    onStartRecording: () -> Unit,
    onStopRecordingAndSend: () -> Unit,
    chatViewModel: ChatViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = onStartRecording, modifier = Modifier.fillMaxWidth()) {
            Text("🎧 開始錄音")
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onStopRecordingAndSend, modifier = Modifier.fillMaxWidth()) {
            Text("✅ 結束並上傳")
        }
        Spacer(modifier = Modifier.height(40.dp))
        ChatUI(viewModel = chatViewModel)
    }
}

@Composable
fun ChatUI(viewModel: ChatViewModel = viewModel()) {
    var userInput by remember { mutableStateOf("") }
    val reply by viewModel.reply.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("輸入對話內容") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            viewModel.sendMessage(userInput)
        }) {
            Text("送出")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI 回覆：$reply")
    }
}

