package com.soliano.betvalueanalyzer.ui.components

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val decimalFormat = NumberFormat.getNumberInstance(Locale.FRANCE).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

fun formatOdds(value: Double): String = decimalFormat.format(value)
fun formatMoney(value: Double): String = "${decimalFormat.format(value)} €"
fun formatPercent(value: Double): String = "${(value * 100).toInt()} %"
fun formatSignedPercent(value: Double): String = "%+.1f %%".format(Locale.FRANCE, value * 100)
fun formatDate(timestamp: Long): String = SimpleDateFormat("dd MMM · HH:mm", Locale.FRANCE).format(Date(timestamp))

