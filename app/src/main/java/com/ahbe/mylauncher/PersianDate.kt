package com.ahbe.mylauncher

import java.util.Calendar
import java.util.Date

internal object PersianDate {
    private val weekDays = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")
    private val months = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    fun todayLong(): String = formatLong(Date())

    fun formatLong(date: Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        val (jy, jm, jd) = gregorianToJalali(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        val weekDay = weekDays[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        return "$weekDay ${jd.toString().toPersianDigits()} ${months[jm - 1]} ${jy.toString().toPersianDigits()}"
    }

    fun formatCompact(epochMillis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val (jy, jm, jd) = gregorianToJalali(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        return "%04d/%02d/%02d".format(jy, jm, jd).toPersianDigits()
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var jy: Int
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return intArrayOf(jy, jm, jd)
    }
}

internal fun String.toPersianDigits(): String = buildString(length) {
    this@toPersianDigits.forEach { ch ->
        append(if (ch in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[ch - '0'] else ch)
    }
}
