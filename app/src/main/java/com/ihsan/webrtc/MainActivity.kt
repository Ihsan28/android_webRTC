package com.ihsan.webrtc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.ihsan.webrtc.databinding.ActivityStartBinding

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStartBinding
    private val db = Firebase.firestore
    private val REQUEST_CODE_PERMISSIONS = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val meetingId = binding.meetingId
        Constants.isIntiatedNow = true
        Constants.isCallEnded = true
        checkPermissions()

        binding.startMeeting.setOnClickListener {
            /*if (meetingId.text.toString().trim().isEmpty())
                meetingId.error = "Please enter meeting id"
            else {*/
                db.collection("calls")
                    .document("X5x76lOHFbKqtfnCOVat")
                    .get()
                    .addOnSuccessListener {
                        /*if (it["type"] == "OFFER" || it["type"] == "ANSWER" || it["type"] == "END_CALL") {
                            meetingId.error = "Please enter new meeting ID"
                        } else {*/

                            val intent = Intent(this, RTCActivity::class.java)
                            intent.putExtra("meetingID", "X5x76lOHFbKqtfnCOVat")
                            intent.putExtra("isJoin", false)
                            startActivity(intent)
                        /*}*/
                        Toast.makeText(this, "${it.data?.keys}", Toast.LENGTH_SHORT).show()
                        Log.d("mainActivity", "onCreate: success")
                    }
                    .addOnFailureListener {
                        meetingId.error = "Please enter new meeting ID"
                    }
            /*}*/
        }
        binding.joinMeeting.setOnClickListener {
            /*if (meetingId.text.toString().trim().isEmpty())
                meetingId.error = "Please enter meeting id"
            else {*/
                val intent = Intent(this, RTCActivity::class.java)
                intent.putExtra("meetingID", "X5x76lOHFbKqtfnCOVat")
                intent.putExtra("isJoin", true)
                startActivity(intent)
            /*}*/
        }
    }

    private fun checkPermissions() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}