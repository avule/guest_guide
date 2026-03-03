package com.example.guestguide.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.guestguide.data.model.Contact
import com.example.guestguide.data.model.ContactType
import com.example.guestguide.databinding.ItemContactBinding

class ContactsAdapter(
    private val isAdmin: Boolean,
    private val onContactClick: (Contact) -> Unit,
    private val onDeleteClick: (Contact) -> Unit,
    private val onEditClick: ((Contact) -> Unit)? = null
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Contact) {
            binding.tvName.text = item.name
            binding.tvNumber.text = item.number

            // Ikone prema tipu
            val iconRes = when (item.type) {
                ContactType.TAXI -> android.R.drawable.ic_menu_directions
                ContactType.POLICE -> android.R.drawable.ic_lock_idle_lock
                ContactType.AMBULANCE -> android.R.drawable.stat_sys_warning
                ContactType.OTHER -> android.R.drawable.sym_action_call
            }
            binding.ivIcon.setImageResource(iconRes)

            binding.root.setOnClickListener {
                onContactClick(item)
            }

            if (isAdmin) {
                binding.adminButtons.visibility = View.VISIBLE

                binding.btnDelete.setOnClickListener {
                    onDeleteClick(item)
                }

                binding.btnEdit.setOnClickListener {
                    onEditClick?.invoke(item)
                }
            } else {
                binding.adminButtons.visibility = View.GONE
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Contact, newItem: Contact): Boolean {
            return oldItem == newItem
        }
    }
}