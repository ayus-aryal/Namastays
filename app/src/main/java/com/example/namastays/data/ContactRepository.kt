package com.example.namastays.data

import kotlinx.coroutines.flow.Flow

class ContactRepository(private val dao: ContactDao) {

    fun getAllContacts(): Flow<List<EmergencyContactEntity>> = dao.getAllContacts()

    suspend fun addContact(contact: EmergencyContactEntity) {
        dao.insertContact(contact)
    }

    suspend fun deleteContact(contact: EmergencyContactEntity) {
        dao.deleteContact(contact)
    }
}