package com.app.checkot.viewmodel

import com.app.checkot.model.ChatMessage
import com.app.checkot.model.ChatThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelTest {

    @Test
    fun `ChatMessage constructs with correct defaults`() {
        val msg = ChatMessage(
            messageId = "msg_123",
            chatId = "booking_99",
            senderId = "user_abc",
            senderRole = "customer",
            text = "I've arrived!",
            timestamp = 1700000000000L
        )

        assertEquals("msg_123", msg.messageId)
        assertEquals("booking_99", msg.chatId)
        assertEquals("user_abc", msg.senderId)
        assertEquals("customer", msg.senderRole)
        assertEquals("I've arrived!", msg.text)
        assertEquals(1700000000000L, msg.timestamp)
        assertFalse(msg.isRead)
    }

    @Test
    fun `ChatThread handles unread counts correctly`() {
        val thread = ChatThread(
            chatId = "booking_99",
            bookingId = "booking_99",
            shopId = "shop_1",
            userId = "user_abc",
            customerName = "John Doe",
            shopName = "Sparkle Wash",
            lastMessage = "Ready for pickup!",
            lastMessageTimestamp = 1700000000000L,
            unreadCountCustomer = 1,
            unreadCountOwner = 0
        )

        assertEquals("booking_99", thread.chatId)
        assertEquals("Sparkle Wash", thread.shopName)
        assertEquals(1, thread.unreadCountCustomer)
        assertEquals(0, thread.unreadCountOwner)
    }
}
