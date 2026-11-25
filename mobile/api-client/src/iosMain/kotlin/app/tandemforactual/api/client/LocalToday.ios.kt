package app.tandemforactual.api.client

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

internal actual fun localTodayIsoDate(): String =
    NSDateFormatter()
        .apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
            timeZone = NSTimeZone.localTimeZone
            dateFormat = "yyyy-MM-dd"
        }.stringFromDate(NSDate())
