package com.example.universaldeviceconnector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: List<AIAssistantActivity.ChatMessage>) : 
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    
    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_chat_user
        } else {
            R.layout.item_chat_ai
        }
        
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ChatViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }
    
    override fun getItemCount(): Int = messages.size
    
    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val senderText: TextView? = itemView.findViewById(R.id.senderText)
        
        fun bind(message: AIAssistantActivity.ChatMessage) {
            messageText.text = message.message
            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeText.text = timeFormat.format(Date(message.timestamp))
            
            senderText?.text = message.sender
        }
    }
}
