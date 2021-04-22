package com.reach_android.ui.pin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.reach_android.R
import com.reach_android.ui.RemoteSupportViewModel
import kotlinx.android.synthetic.main.fragment_pin.*
import kotlinx.coroutines.ExperimentalCoroutinesApi


class PinFragment : Fragment() {

    private val rsViewModel: RemoteSupportViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pin, container, false)
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pinBackButton.setOnClickListener {
            findNavController().navigateUp()
        }

        pinContinueButton.setOnClickListener {
            val inputManager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val binder = view.windowToken
            inputManager.hideSoftInputFromWindow(binder, InputMethodManager.HIDE_NOT_ALWAYS)

            pinProgressBar.visibility = View.VISIBLE
            pinContinueButton.visibility = View.INVISIBLE

            val pin = pinTextField.text.toString().trim()
            val connect = rsViewModel.connectToSupport(pin)
            connect.observe(viewLifecycleOwner) {
                connect.removeObservers(viewLifecycleOwner)

                if (it != null) {
                    Toast.makeText(requireActivity(), it, Toast.LENGTH_SHORT).show()
                } else {
                    findNavController().navigate(R.id.action_pinFragment_to_supportFragment)
                    pinTextField.setText("")
                }

                pinProgressBar.visibility = View.GONE
                pinContinueButton.visibility = View.VISIBLE
            }
        }
    }
}