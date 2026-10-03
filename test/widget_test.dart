import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:clock_widget/main.dart';
import 'package:clock_widget/update.dart';

void main() {
  updateTests();
  rotationTest();

  testWidgets('theme picker lets you swipe through every clock face', (WidgetTester tester) async {
    await tester.pumpWidget(const ClockWidgetApp());
    await tester.pumpAndSettle();

    expect(find.text('Clock Widget'), findsOneWidget);
    expect(find.text('Apply to widget'), findsOneWidget);

    for (final title in ['Pastel Bubbles', 'Green LED', 'Flip Clock', 'Minimal']) {
      expect(find.text(title), findsWidgets);
      await tester.drag(find.byType(PageView), const Offset(-300, 0));
      await tester.pumpAndSettle();
    }
  });
}

void rotationTest() {
  testWidgets('rotating the screen keeps a single carousel (no controller assertion)', (WidgetTester tester) async {
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    tester.view.physicalSize = const Size(420, 860);
    await tester.pumpWidget(const ClockWidgetApp());
    await tester.pumpAndSettle();
    await tester.drag(find.byType(PageView), const Offset(-250, 0));
    await tester.pumpAndSettle();

    for (final size in const [Size(860, 420), Size(420, 860), Size(860, 420)]) {
      tester.view.physicalSize = size;
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.byType(PageView), findsOneWidget);
    }
  });
}

void updateTests() {
  test('UpdateInfo reads what the native check returns', () {
    final info = UpdateInfo.fromMap({
      'version': '1.1.0',
      'notes': ['New face: Neon', 'Fixes'],
      'downloadUrl': 'https://github.com/ahmadarif-lab/clock-widget/releases/download/v1.1.0/x.apk',
      'dismissed': false,
    });
    expect(info.version, '1.1.0');
    expect(info.notes.first, 'New face: Neon');
    expect(info.dismissed, isFalse);
  });

  testWidgets('update banner shows the version and the first note, and reports taps', (WidgetTester tester) async {
    var downloaded = false;
    var dismissed = false;
    const info = UpdateInfo(version: '1.1.0', notes: ['New face: Neon'], downloadUrl: 'https://github.com/x', dismissed: false);
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: UpdateBanner(update: info, accent: Colors.green, onDownload: () => downloaded = true, onDismiss: () => dismissed = true),
      ),
    ));

    expect(find.textContaining('1.1.0'), findsOneWidget);
    expect(find.text('New face: Neon'), findsOneWidget);
    await tester.tap(find.text('Download'));
    await tester.tap(find.byIcon(Icons.close_rounded));
    expect(downloaded, isTrue);
    expect(dismissed, isTrue);
  });
}
