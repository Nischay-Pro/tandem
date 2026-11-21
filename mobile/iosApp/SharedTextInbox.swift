import Foundation

/// Text shared into Tandem by the share extension, handed to the app through the App Group it shares with it.
enum SharedTextInbox {
    static let appGroup = "group.app.tandemforactual.ios"
    private static let textKey = "pendingSharedText"
    private static let savedAtKey = "pendingSharedTextSavedAt"
    static let maxAge: TimeInterval = 60 * 60
    static let maxLength = 2_000

    static func save(_ text: String, to defaults: UserDefaults?, now: Date = Date()) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let defaults, !trimmed.isEmpty else { return false }
        defaults.set(String(trimmed.prefix(maxLength)), forKey: textKey)
        defaults.set(now.timeIntervalSince1970, forKey: savedAtKey)
        return true
    }

    /// Returns the waiting text once and clears it; text older than an hour is dropped rather than surprising someone later.
    static func take(from defaults: UserDefaults?, now: Date = Date()) -> String? {
        guard let defaults, let text = defaults.string(forKey: textKey) else { return nil }
        let savedAt = defaults.double(forKey: savedAtKey)
        defaults.removeObject(forKey: textKey)
        defaults.removeObject(forKey: savedAtKey)
        return now.timeIntervalSince1970 - savedAt <= maxAge ? text : nil
    }
}
