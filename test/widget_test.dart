import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:clock_widget/main.dart';

void main() {
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
