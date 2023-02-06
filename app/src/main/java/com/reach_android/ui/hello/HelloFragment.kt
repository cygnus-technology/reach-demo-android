package com.reach_android.ui.hello

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.reach_android.R
import com.reach_android.databinding.FragmentHelloBinding
import com.reach_android.util.*
import com.reach_android.ui.ConditionalBackFragment

class HelloFragment : Fragment(R.layout.fragment_hello), ConditionalBackFragment {
    val viewModel: HelloViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentHelloBinding.inflate(inflater, container, false)
        binding.vm = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity.hideActionBar()
    }

    override fun onBackPressed(): Boolean = false

    override fun onUpPressed(): Boolean = false
}
