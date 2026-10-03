import 'dart:ui' show lerpDouble;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const ClockWidgetApp());
}

class ClockWidgetApp extends StatelessWidget {
  const ClockWidgetApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Clock Widget Themes',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple, brightness: Brightness.dark),
        useMaterial3: true,
      ),
      home: const ThemeSelectorScreen(),
    );
  }
}

/// One selectable widget face. [preview] is a screenshot of the real widget.
class ClockTheme {
  const ClockTheme({
    required this.id,
    required this.title,
    required this.description,
    required this.preview,
    required this.accent,
    required this.tags,
  });

  final String id;
  final String title;
  final String description;
  final String preview;
  final Color accent;
  final List<String> tags;
}

const _themes = [
  ClockTheme(
    id: 'bubble',
    title: 'Pastel Bubbles',
    description: 'Chunky, candy-coloured digits with soft spheres.',
    preview: 'assets/previews/bubble.webp',
    accent: Color(0xFFD7A8F0),
    tags: ['Colourful', 'Rounded'],
  ),
  ClockTheme(
    id: 'digital',
    title: 'Green LED',
    description: 'A glowing seven-segment display with battery and date.',
    preview: 'assets/previews/digital.webp',
    accent: Color(0xFF00E676),
    tags: ['Retro', 'Battery', 'Date'],
  ),
  ClockTheme(
    id: 'flip',
    title: 'Flip Clock',
    description: 'Cards that really flip, every second.',
    preview: 'assets/previews/flip.webp',
    accent: Color(0xFFB57BC4),
    tags: ['3D flip', 'Seconds'],
  ),
  ClockTheme(
    id: 'minimal',
    title: 'Minimal',
    description: 'One big white time and a red date. Nothing else.',
    preview: 'assets/previews/minimal.webp',
    accent: Color(0xFFFF4D43),
    tags: ['Clean', 'Bold'],
  ),
];

const _ink = Color(0xFF0A0A10);

class ThemeSelectorScreen extends StatefulWidget {
  const ThemeSelectorScreen({super.key});

  @override
  State<ThemeSelectorScreen> createState() => _ThemeSelectorScreenState();
}

class _ThemeSelectorScreenState extends State<ThemeSelectorScreen> {
  static const platform = MethodChannel('com.example.clock_widget/theme');

  final _pages = PageController(viewportFraction: 0.8);
  // keeps the one PageView alive (and re-parented) when a rotation swaps the portrait / landscape
  // layouts; two PageViews on one controller would assert
  final _carouselKey = GlobalKey();
  String? _applied; // the theme the widget is using right now
  int _index = 0; // the card in the middle of the carousel
  bool _applying = false;

  ClockTheme get _shown => _themes[_index];

  @override
  void initState() {
    super.initState();
    _loadCurrentTheme();
  }

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }

  Future<void> _loadCurrentTheme() async {
    try {
      final id = await platform.invokeMethod<String>('getTheme');
      final i = _themes.indexWhere((t) => t.id == id);
      if (!mounted) return;
      setState(() {
        _applied = id;
        if (i >= 0) _index = i;
      });
      if (i > 0 && _pages.positions.length == 1) _pages.jumpToPage(i);
    } catch (e) {
      debugPrint('Failed to read theme: $e');
    }
  }

  Future<void> _apply() async {
    final theme = _shown;
    if (_applying || _applied == theme.id) return;
    HapticFeedback.mediumImpact();
    setState(() => _applying = true);
    try {
      await platform.invokeMethod('setTheme', {'theme': theme.id});
      if (!mounted) return;
      setState(() => _applied = theme.id);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            behavior: SnackBarBehavior.floating,
            backgroundColor: theme.accent,
            content: Text(
              '${theme.title} is on your home screen',
              style: const TextStyle(color: Colors.black, fontWeight: FontWeight.w600),
            ),
          ),
        );
    } on PlatformException catch (e) {
      debugPrint("Failed to set theme: '${e.message}'.");
    } finally {
      if (mounted) setState(() => _applying = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final accent = _shown.accent;
    return Scaffold(
      backgroundColor: _ink,
      body: TweenAnimationBuilder<Color?>(
        tween: ColorTween(end: accent),
        duration: const Duration(milliseconds: 550),
        builder: (context, color, child) {
          final glow = color ?? accent;
          return DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                center: const Alignment(0, -0.55),
                radius: 1.15,
                colors: [Color.lerp(_ink, glow, 0.42)!, Color.lerp(_ink, glow, 0.1)!, _ink],
                stops: const [0, 0.45, 1],
              ),
            ),
            child: child,
          );
        },
        child: SafeArea(
          child: OrientationBuilder(
            builder: (context, orientation) {
              final landscape = orientation == Orientation.landscape;
              final carousel = _buildCarousel(compact: landscape);
              final dots = _Dots(count: _themes.length, index: _index, accent: accent);
              final button = _ApplyButton(
                accent: accent,
                applied: _applied == _shown.id,
                applying: _applying,
                onPressed: _apply,
              );
              if (!landscape) {
                return Column(
                  children: [
                    const _Header(),
                    Expanded(child: carousel),
                    dots,
                    Padding(padding: const EdgeInsets.fromLTRB(24, 18, 24, 20), child: button),
                  ],
                );
              }
              // landscape: the text and the button on the left, the carousel taking the right side.
              // The info block gets all the space between the header and the dots and sits at the
              // top of it, so a longer description never moves the title, the dots or the button.
              return Row(
                children: [
                  Expanded(
                    flex: 4,
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(28, 12, 12, 16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const _Header(compact: true),
                          const SizedBox(height: 14),
                          Expanded(
                            child: SingleChildScrollView(
                              child: AnimatedSwitcher(
                                duration: const Duration(milliseconds: 300),
                                layoutBuilder: (current, previous) => Stack(
                                  alignment: Alignment.topLeft,
                                  children: [...previous, ?current],
                                ),
                                child: _Info(key: ValueKey(_shown.id), theme: _shown),
                              ),
                            ),
                          ),
                          const SizedBox(height: 12),
                          dots,
                          const SizedBox(height: 14),
                          button,
                        ],
                      ),
                    ),
                  ),
                  Expanded(flex: 6, child: carousel),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildCarousel({required bool compact}) {
    return PageView.builder(
      key: _carouselKey,
      controller: _pages,
      itemCount: _themes.length,
      onPageChanged: (i) {
        HapticFeedback.selectionClick();
        setState(() => _index = i);
      },
      itemBuilder: (context, i) => AnimatedBuilder(
        animation: _pages,
        builder: (context, _) {
          final ready = _pages.positions.length == 1 && _pages.position.haveDimensions;
          final page = ready ? (_pages.page ?? _index.toDouble()) : _index.toDouble();
          final distance = (page - i).abs().clamp(0.0, 1.0);
          return Transform.scale(
            scale: lerpDouble(1, 0.86, distance)!,
            child: Opacity(
              opacity: lerpDouble(1, 0.45, distance)!,
              child: _ThemeCard(
                theme: _themes[i],
                active: _applied == _themes[i].id,
                parallax: (page - i).clamp(-1.0, 1.0),
                compact: compact,
              ),
            ),
          );
        },
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({this.compact = false});

  final bool compact;

  @override
  Widget build(BuildContext context) {
    if (compact) {
      return const Text(
        'Clock Widget',
        style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: -0.5),
      );
    }
    return const Padding(
      padding: EdgeInsets.fromLTRB(28, 18, 28, 8),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Clock Widget',
              style: TextStyle(fontSize: 32, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: -0.5),
            ),
            SizedBox(height: 4),
            Text(
              'Swipe to pick a face for your home screen',
              style: TextStyle(fontSize: 14, color: Colors.white60, letterSpacing: 0.2),
            ),
          ],
        ),
      ),
    );
  }
}

class _ThemeCard extends StatelessWidget {
  const _ThemeCard({required this.theme, required this.active, required this.parallax, this.compact = false});

  final ClockTheme theme;
  final bool active;
  final double parallax; // -1 (card is to the left) .. 1 (to the right)
  final bool compact; // landscape: only the preview, the text lives beside the carousel

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(32),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Colors.white.withValues(alpha: 0.16), Colors.white.withValues(alpha: 0.04)],
          ),
          border: Border.all(color: Colors.white.withValues(alpha: 0.18), width: 1.2),
          boxShadow: [BoxShadow(color: theme.accent.withValues(alpha: 0.28), blurRadius: 48, offset: const Offset(0, 24))],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(32),
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (compact)
                  Align(alignment: Alignment.centerRight, child: active ? _ActiveBadge(color: theme.accent) : const SizedBox(height: 26))
                else
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(child: Wrap(runSpacing: 6, children: [for (final tag in theme.tags) _Tag(label: tag, color: theme.accent)])),
                      if (active) _ActiveBadge(color: theme.accent),
                    ],
                  ),
                Expanded(
                  child: Center(
                    child: Transform.translate(
                      // the preview drifts against the swipe for a bit of depth
                      offset: Offset(parallax * -28, 0),
                      child: _Preview(theme: theme, floorGlow: !compact),
                    ),
                  ),
                ),
                if (!compact) _Info(theme: theme, showTags: false),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The widget screenshot with an accent glow behind it and, optionally, a soft pool of light on
/// the "floor" below it.
class _Preview extends StatelessWidget {
  const _Preview({required this.theme, required this.floorGlow});

  final ClockTheme theme;
  final bool floorGlow;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(color: theme.accent.withValues(alpha: 0.35), blurRadius: 60, spreadRadius: 4),
              BoxShadow(color: Colors.black.withValues(alpha: 0.5), blurRadius: 24, offset: const Offset(0, 14)),
            ],
          ),
          child: AspectRatio(aspectRatio: 680 / 378, child: Image.asset(theme.preview, fit: BoxFit.cover)),
        ),
        if (floorGlow)
          Padding(
            padding: const EdgeInsets.only(top: 26),
            child: FractionallySizedBox(
              widthFactor: 0.62,
              child: DecoratedBox(
                // no fill: only the blurred shadow is painted, so there is no hard edge
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8),
                  boxShadow: [BoxShadow(color: theme.accent.withValues(alpha: 0.5), blurRadius: 34, spreadRadius: 4)],
                ),
                child: const SizedBox(height: 4),
              ),
            ),
          ),
      ],
    );
  }
}

/// Title, description and (optionally) the tags of a theme.
class _Info extends StatelessWidget {
  const _Info({super.key, required this.theme, this.showTags = true});

  final ClockTheme theme;
  final bool showTags;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          theme.title,
          style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: Colors.white, letterSpacing: -0.4),
        ),
        const SizedBox(height: 6),
        Text(theme.description, style: const TextStyle(fontSize: 15, height: 1.35, color: Colors.white70)),
        if (showTags) ...[
          const SizedBox(height: 12),
          Wrap(runSpacing: 6, children: [for (final tag in theme.tags) _Tag(label: tag, color: theme.accent)]),
        ],
      ],
    );
  }
}

class _Tag extends StatelessWidget {
  const _Tag({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(right: 6),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.45)),
      ),
      child: Text(label, style: TextStyle(fontSize: 11.5, fontWeight: FontWeight.w600, color: color, letterSpacing: 0.3)),
    );
  }
}

class _ActiveBadge extends StatelessWidget {
  const _ActiveBadge({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(8, 5, 12, 5),
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(20)),
      child: const Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.check_circle_rounded, size: 15, color: Colors.black87),
          SizedBox(width: 4),
          Text('IN USE', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w800, color: Colors.black87, letterSpacing: 0.8)),
        ],
      ),
    );
  }
}

class _Dots extends StatelessWidget {
  const _Dots({required this.count, required this.index, required this.accent});

  final int count;
  final int index;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        for (var i = 0; i < count; i++)
          AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOutCubic,
            margin: const EdgeInsets.symmetric(horizontal: 4),
            width: i == index ? 28 : 8,
            height: 8,
            decoration: BoxDecoration(
              color: i == index ? accent : Colors.white24,
              borderRadius: BorderRadius.circular(8),
            ),
          ),
      ],
    );
  }
}

class _ApplyButton extends StatelessWidget {
  const _ApplyButton({required this.accent, required this.applied, required this.applying, required this.onPressed});

  final Color accent;
  final bool applied;
  final bool applying;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final Widget label;
    if (applying) {
      label = const SizedBox(key: ValueKey('busy'), width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.6, color: Colors.black87));
    } else if (applied) {
      label = const Row(
        key: ValueKey('applied'),
        mainAxisSize: MainAxisSize.min,
        children: [Icon(Icons.check_rounded, color: Colors.white), SizedBox(width: 8), Text('On your home screen')],
      );
    } else {
      label = const Row(
        key: ValueKey('apply'),
        mainAxisSize: MainAxisSize.min,
        children: [Icon(Icons.auto_awesome_rounded, color: Colors.black87), SizedBox(width: 8), Text('Apply to widget')],
      );
    }
    return AnimatedContainer(
      duration: const Duration(milliseconds: 350),
      height: 58,
      width: double.infinity,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        color: applied ? Colors.white.withValues(alpha: 0.12) : accent,
        border: applied ? Border.all(color: Colors.white24) : null,
        boxShadow: applied ? const [] : [BoxShadow(color: accent.withValues(alpha: 0.5), blurRadius: 24, offset: const Offset(0, 10))],
      ),
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: applied || applying ? null : onPressed,
          child: Center(
            child: DefaultTextStyle(
              style: TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: applied ? Colors.white : Colors.black87,
                letterSpacing: 0.2,
              ),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: FittedBox(
                  fit: BoxFit.scaleDown,
                  child: AnimatedSwitcher(duration: const Duration(milliseconds: 250), child: label),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
