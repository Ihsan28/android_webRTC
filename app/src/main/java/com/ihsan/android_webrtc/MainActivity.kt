package com.ihsan.android_webrtc

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.ihsan.android_webrtc.ui.fragments.StartFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, StartFragment())
                .commit()
        }
    }
}