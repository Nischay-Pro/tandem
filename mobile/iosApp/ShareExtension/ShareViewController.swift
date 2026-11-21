import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private let card = UIView()
    private let titleLabel = UILabel()
    private let detailLabel = UILabel()
    private let doneButton = UIButton(type: .system)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.black.withAlphaComponent(0.25)
        layoutCard()
        loadSharedText { [weak self] text in
            let saved = text.map { SharedTextInbox.save($0, to: UserDefaults(suiteName: SharedTextInbox.appGroup)) } ?? false
            self?.show(saved: saved)
        }
    }

    private func loadSharedText(_ completion: @escaping (String?) -> Void) {
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        guard let provider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) }) else {
            completion(nil)
            return
        }
        provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) { item, _ in
            let text = (item as? String) ?? (item as? Data).flatMap { String(data: $0, encoding: .utf8) }
            DispatchQueue.main.async { completion(text) }
        }
    }

    private func show(saved: Bool) {
        titleLabel.text = saved ? "Saved to Tandem" : "Nothing to add"
        detailLabel.text = saved
            ? "Open Tandem to finish adding this transaction."
            : "Share text from a bank message or receipt to start a transaction."
        doneButton.isHidden = false
        UIAccessibility.post(notification: .screenChanged, argument: titleLabel)
    }

    private func layoutCard() {
        card.backgroundColor = .systemBackground
        card.layer.cornerRadius = 22
        card.layer.cornerCurve = .continuous
        card.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(card)

        titleLabel.font = .preferredFont(forTextStyle: .headline)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.text = "Adding to Tandem…"
        detailLabel.font = .preferredFont(forTextStyle: .subheadline)
        detailLabel.adjustsFontForContentSizeCategory = true
        detailLabel.textColor = .secondaryLabel
        detailLabel.numberOfLines = 0
        var configuration = UIButton.Configuration.filled()
        configuration.title = "Done"
        configuration.baseBackgroundColor = UIColor(red: 0.44, green: 0.28, blue: 0.91, alpha: 1)
        configuration.cornerStyle = .large
        doneButton.configuration = configuration
        doneButton.isHidden = true
        doneButton.addAction(UIAction { [weak self] _ in
            self?.extensionContext?.completeRequest(returningItems: nil)
        }, for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [titleLabel, detailLabel, doneButton])
        stack.axis = .vertical
        stack.spacing = 12
        stack.setCustomSpacing(20, after: detailLabel)
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            card.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            card.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            card.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            card.widthAnchor.constraint(lessThanOrEqualToConstant: 420),
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 24),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -20),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),
            doneButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 48),
        ])
    }
}
