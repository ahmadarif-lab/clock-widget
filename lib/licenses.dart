import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Adds the licences of the bundled fonts and of this app to Flutter's licence page.
///
/// The SIL Open Font License asks for its text to travel with the font, and the fonts end up inside
/// the APK, so the texts are bundled as assets (see `licenses/` and pubspec.yaml) and listed here.
void registerLicenses() {
  const entries = <(List<String>, String)>[
    (['Clock Widget'], 'LICENSE'),
    (['Lilita One (Pastel Bubbles digits)'], 'licenses/LilitaOne-OFL.txt'),
    (['League Gothic (Flip Clock digits)'], 'licenses/LeagueGothic-OFL.txt'),
    (['Bebas Neue (Minimal face)'], 'licenses/BebasNeue-OFL.txt'),
    (['7 Seg Classic, derived from DSEG7 Classic (LED text)'], 'licenses/DSEG-OFL.txt'),
  ];
  LicenseRegistry.addLicense(() async* {
    for (final (packages, asset) in entries) {
      yield LicenseEntryWithLineBreaks(packages, await rootBundle.loadString(asset));
    }
  });
}
