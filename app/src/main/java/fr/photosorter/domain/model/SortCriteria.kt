package fr.photosorter.domain.model

data class SortCriteria(val field: SortField, val ascending: Boolean)

enum class SortField { NAME, DATE, SIZE, FORMAT, LABEL }
