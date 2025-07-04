package com.bogdan.waterreminder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class PremiumFragment : Fragment() {

    private var isPremium: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_premium, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnSubscribe = view.findViewById<Button>(R.id.btn_subscribe)
        val textUserStatus = view.findViewById<TextView>(R.id.text_user_status)

        // Setați statusul default ca normal
        textUserStatus?.text = getString(R.string.user_status_normal)

        // Dacă userul e premium, schimbă statusul
        (activity as? MainActivity)?.checkSubscriptionStatus { isActive ->
            isPremium = isActive
            if (isPremium) {
                textUserStatus?.text = getString(R.string.user_status_premium)
            }
        }

        btnSubscribe?.setOnClickListener {
            if (isPremium) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.already_premium_toast),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                (activity as? MainActivity)?.startSubscriptionPurchase()
            }
        }
    }
}