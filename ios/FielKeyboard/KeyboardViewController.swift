import UIKit
import AVFoundation

/// iOS system keyboard extension. Enable it in Settings → Keyboard → Keyboards → Fiel.
/// Requires "Allow Full Access" so the extension can use the microphone and reach Fiel.
final class KeyboardViewController: UIInputViewController, AVAudioRecorderDelegate {
    private let status = UILabel()
    private let mic = UIButton(type: .custom)
    private var recorder: AVAudioRecorder?
    private var fileURL: URL?
    private var holding = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.047, green: 0.047, blue: 0.043, alpha: 1)
        let title = UILabel()
        title.text = "Fiel"
        title.font = UIFont.italicSystemFont(ofSize: 20)
        title.textColor = UIColor(red: 0.953, green: 0.937, blue: 0.902, alpha: 1)

        status.text = "Mantén pulsado y habla"
        status.font = UIFont.systemFont(ofSize: 13)
        status.textColor = UIColor(red: 0.604, green: 0.584, blue: 0.549, alpha: 1)
        status.textAlignment = .center

        mic.backgroundColor = UIColor(red: 0.953, green: 0.937, blue: 0.902, alpha: 1)
        mic.layer.cornerRadius = 36
        mic.addTarget(self, action: #selector(down), for: .touchDown)
        mic.addTarget(self, action: #selector(up), for: [.touchUpInside, .touchUpOutside, .touchCancel])

        let globe = UIButton(type: .system)
        globe.setTitle("🌐", for: .normal)
        globe.addTarget(self, action: #selector(nextKb), for: .touchUpInside)

        let space = key("espacio") { [weak self] in self?.textDocumentProxy.insertText(" ") }
        let del = key("⌫") { [weak self] in self?.textDocumentProxy.deleteBackward() }
        let enter = key("↵") { [weak self] in self?.textDocumentProxy.insertText("\n") }

        let row = UIStackView(arrangedSubviews: [del, space, enter])
        row.axis = .horizontal
        row.spacing = 8
        row.distribution = .fillEqually

        [title, globe, status, mic, row].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview($0)
        }
        NSLayoutConstraint.activate([
            title.topAnchor.constraint(equalTo: view.topAnchor, constant: 10),
            title.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            globe.centerYAnchor.constraint(equalTo: title.centerYAnchor),
            globe.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            status.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 6),
            status.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            mic.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            mic.topAnchor.constraint(equalTo: status.bottomAnchor, constant: 12),
            mic.widthAnchor.constraint(equalToConstant: 72),
            mic.heightAnchor.constraint(equalToConstant: 72),
            row.topAnchor.constraint(equalTo: mic.bottomAnchor, constant: 16),
            row.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            row.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
            row.heightAnchor.constraint(equalToConstant: 44),
            row.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -10),
        ])
    }

    private func key(_ title: String, action: @escaping () -> Void) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.setTitleColor(UIColor(red: 0.953, green: 0.937, blue: 0.902, alpha: 1), for: .normal)
        b.backgroundColor = UIColor(red: 0.110, green: 0.106, blue: 0.094, alpha: 1)
        b.layer.cornerRadius = 10
        b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return b
    }

    @objc private func nextKb() { advanceToNextInputMode() }

    @objc private func down() {
        holding = true
        mic.backgroundColor = UIColor(red: 0.769, green: 0.361, blue: 0.290, alpha: 1)
        status.text = "Escuchando…"
        let dir = FileManager.default.temporaryDirectory
        let url = dir.appendingPathComponent("fiel.wav")
        fileURL = url
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVSampleRateKey: 16000,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
        ]
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)
            recorder = try AVAudioRecorder(url: url, settings: settings)
            recorder?.record()
        } catch {
            status.text = "Sin micrófono. Activa Acceso total."
        }
    }

    @objc private func up() {
        guard holding else { return }
        holding = false
        mic.backgroundColor = UIColor(red: 0.953, green: 0.937, blue: 0.902, alpha: 1)
        recorder?.stop()
        recorder = nil
        status.text = "Pasando a texto…"
        guard let url = fileURL, let data = try? Data(contentsOf: url) else {
            status.text = "Audio vacío"
            return
        }
        transcribe(data)
    }

    private func transcribe(_ wav: Data) {
        let defaults = UserDefaults(suiteName: "group.app.fiel.ime") ?? .standard
        guard let api = defaults.string(forKey: "api"),
              let token = defaults.string(forKey: "token"),
              let endpoint = URL(string: api + "/api/keyboard-transcribe") else {
            status.text = "Pega la clave en la app Fiel"
            return
        }
        var req = URLRequest(url: endpoint)
        req.httpMethod = "POST"
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let boundary = "Fiel\(UUID().uuidString)"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        func field(_ name: String, _ value: String) {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n".data(using: .utf8)!)
        }
        field("language", defaults.string(forKey: "lang") ?? "es")
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: audio/wav\r\n\r\n".data(using: .utf8)!)
        body.append(wav)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        req.httpBody = body
        URLSession.shared.dataTask(with: req) { data, _, error in
            DispatchQueue.main.async {
                if let error {
                    self.status.text = error.localizedDescription
                    return
                }
                guard let data,
                      let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    self.status.text = "Respuesta ilegible"
                    return
                }
                if let text = obj["text"] as? String, (obj["ok"] as? Bool) == true {
                    let proxy = self.textDocumentProxy
                    proxy.insertText(text)
                    self.status.text = "Listo"
                } else {
                    self.status.text = (obj["error"] as? String) ?? "Error"
                }
            }
        }.resume()
    }
}
