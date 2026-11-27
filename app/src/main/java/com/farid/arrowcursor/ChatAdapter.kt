package com.farid.arrowcursor

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    data class Message(val text: String, val isUser: Boolean)

    private val messages = mutableListOf<Message>()

    fun addMessage(m: Message) {
        messages.add(m)
        notifyItemInserted(messages.size - 1)
    }

    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            messages.removeAt(messages.size - 1)
            notifyItemRemoved(messages.size)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.text_message)
        val container: LinearLayout = itemView.findViewById(R.id.message_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.textView.text = message.text
        val colorRes = if (message.isUser) R.color.chat_user_bg else R.color.chat_assistant_bg
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(ContextCompat.getColor(holder.itemView.context, colorRes))
        }
        holder.textView.background = drawable
        holder.container.gravity = if (message.isUser) Gravity.END else Gravity.START
    }

    override fun getItemCount(): Int = messages.size
}
