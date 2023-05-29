package com.ihsan.android_webrtc.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.ihsan.android_webrtc.R
import com.ihsan.android_webrtc.databinding.FragmentStartBinding
import com.ihsan.android_webrtc.utils.Constants

class StartFragment : Fragment() {
    private lateinit var binding: FragmentStartBinding
    private val db = Firebase.firestore
    private val REQUEST_CODE_PERMISSIONS = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentStartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val meetingId = binding.meetingId
        Constants.isIntiatedNow = true
        Constants.isCallEnded = true
        checkPermissions()

        binding.startMeeting.setOnClickListener {
            if (meetingId.text.toString().trim().isEmpty())
                meetingId.error = "Please enter meeting id"
            else {
                db.collection("calls")
                    .document(binding.meetingId.text.toString().trim())
                    .get()
                    .addOnSuccessListener {
                        if (it["type"] == "OFFER" || it["type"] == "ANSWER" || it["type"] == "END_CALL") {
                            meetingId.error = "Please enter new meeting ID"
                        } else {
                            navigateToMeetingFragment(
                                binding.meetingId.text.toString().trim(),
                                false
                            )
                        }
                        Toast.makeText(requireContext(), "${it.data?.keys}", Toast.LENGTH_SHORT)
                            .show()
                        Log.d("mainActivity", "onCreate: success")
                    }
                    .addOnFailureListener {
                        meetingId.error = "Please enter new meeting ID"
                    }
            }
        }
        binding.joinMeeting.setOnClickListener {
            if (meetingId.text.toString().trim().isEmpty())
                meetingId.error = "Please enter meeting id"
            else {
                navigateToMeetingFragment(binding.meetingId.text.toString().trim(), true)
            }
        }
    }

    private fun navigateToMeetingFragment(parameter: String, isJoin: Boolean) {
        val fragment = MeetingFragment()
        val args = Bundle()
        args.putString("meetingID", parameter)
        args.putBoolean("isJoin", isJoin)
        fragment.arguments = args

        val fragmentManager = requireActivity().supportFragmentManager
        val transaction = fragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        // Optional: Add the transaction to the back stack, so the user can navigate back
        transaction.addToBackStack(null)
        // Commit the transaction
        transaction.commit()
    }

    private fun checkPermissions() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }
}