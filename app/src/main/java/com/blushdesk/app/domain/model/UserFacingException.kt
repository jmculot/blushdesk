package com.blushdesk.app.domain.model

/**
 * A failure whose [message] is written for the operator ("Mark the order as paid first") and can be
 * shown as-is. Every other exception is logged and replaced by a generic message, so raw database
 * or library errors never reach the screen.
 */
class UserFacingException(message: String) : Exception(message)
