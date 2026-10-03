import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A newer release found by the native update check (see UpdateChecker.kt).
class UpdateInfo {
  const UpdateInfo({required this.version, required this.notes, required this.downloadUrl, required this.dismissed});

  final String version;
  final List<String> notes;
  final String downloadUrl;
  final bool dismissed;

  factory UpdateInfo.fromMap(Map<Object?, Object?> map) => UpdateInfo(
        version: map['version'] as String,
        notes: [for (final note in (map['notes'] as List<Object?>? ?? const [])) note as String],
        downloadUrl: map['downloadUrl'] as String,
        dismissed: map['dismissed'] == true,
      );
}

/// Talks to the native side, which does the network work and the version comparison.
class UpdateService {
  static const _channel = MethodChannel('id.my.bontot.clock_widget/update');

  /// The newest release if it is newer than this install, else null. Never throws: an update
  /// check that fails (offline, site down) just means no banner.
  static Future<UpdateInfo?> check({bool force = false}) async {
    try {
      final map = await _channel.invokeMapMethod<Object?, Object?>('check', {'force': force});
      return map == null ? null : UpdateInfo.fromMap(map);
    } catch (e) {
      debugPrint('Update check failed: $e');
      return null;
    }
  }

  static Future<void> dismiss(UpdateInfo update) => _safe(() => _channel.invokeMethod('dismiss', {'version': update.version}));

  static Future<void> open(UpdateInfo update) => _safe(() => _channel.invokeMethod('open', {'url': update.downloadUrl}));

  /// Asks for the Android 13+ notification permission once, so a new version can be announced.
  static Future<void> requestNotifications() => _safe(() => _channel.invokeMethod('requestNotifications'));

  static Future<void> _safe(Future<void> Function() call) async {
    try {
      await call();
    } catch (e) {
      debugPrint('Update call failed: $e');
    }
  }
}

/// "Update available" strip shown above the carousel (portrait) or beside it (landscape).
class UpdateBanner extends StatelessWidget {
  const UpdateBanner({super.key, required this.update, required this.accent, required this.onDownload, required this.onDismiss});

  final UpdateInfo update;
  final Color accent;
  final VoidCallback onDownload;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final summary = update.notes.isEmpty ? 'New faces and fixes' : update.notes.first;
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        color: Colors.white.withValues(alpha: 0.1),
        border: Border.all(color: accent.withValues(alpha: 0.55), width: 1.2),
        boxShadow: [BoxShadow(color: accent.withValues(alpha: 0.2), blurRadius: 24)],
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 10, 6, 10),
        child: Row(
          children: [
            Icon(Icons.system_update_alt_rounded, color: accent, size: 26),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'Update ${update.version}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w800, color: Colors.white),
                  ),
                  Text(summary, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12.5, color: Colors.white70)),
                ],
              ),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: onDownload,
              style: FilledButton.styleFrom(
                backgroundColor: accent,
                foregroundColor: Colors.black87,
                padding: const EdgeInsets.symmetric(horizontal: 14),
                minimumSize: const Size(0, 38),
              ),
              child: const Text('Download', style: TextStyle(fontWeight: FontWeight.w800)),
            ),
            IconButton(
              onPressed: onDismiss,
              tooltip: 'Not now',
              icon: const Icon(Icons.close_rounded, color: Colors.white54, size: 20),
            ),
          ],
        ),
      ),
    );
  }
}
