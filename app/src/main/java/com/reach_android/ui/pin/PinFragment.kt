package com.reach_android.ui.pin

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.reach_android.R
import com.reach_android.util.*
import com.reach_android.ui.RemoteSupportViewModel
import kotlinx.android.synthetic.main.fragment_pin.*
import kotlinx.coroutines.launch


class PinFragment : Fragment(R.layout.fragment_pin) {

    private val rsViewModel: RemoteSupportViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.showActionBar("Connect to Remote Support")

        pinTextField.addTextChangedListener {
            val pin = it?.toString()?.trim() ?: ""
            pinContinueButton.isEnabled = pin.length == 5
        }

        pinBackButton.setOnClickListener {
            findNavController().navigateUp()
        }

        pinContinueButton.setOnClickListener {
            val inputManager =
                view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val binder = view.windowToken
            inputManager.hideSoftInputFromWindow(binder, InputMethodManager.HIDE_NOT_ALWAYS)

            pinProgressBar.visibility = View.VISIBLE
            pinContinueButton.visibility = View.INVISIBLE

            val pin = pinTextField.text.toString().trim()
            lifecycleScope.launch {
                val error = rsViewModel.connectToSupport(pin)
                if (error != null) {
                    Toast.makeText(requireActivity(), error, Toast.LENGTH_SHORT).show()
                } else {
                    navigate(PinFragmentDirections.actionGlobalSupportDeviceFragment())
                    pinTextField.setText("")
                }

                pinProgressBar.visibility = View.GONE
                pinContinueButton.visibility = View.VISIBLE
            }
        }
    }
}