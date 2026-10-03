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
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const ThemeSelectorScreen(),
    );
  }
}

class ThemeSelectorScreen extends StatefulWidget {
  const ThemeSelectorScreen({super.key});

  @override
  State<ThemeSelectorScreen> createState() => _ThemeSelectorScreenState();
}

class _ThemeSelectorScreenState extends State<ThemeSelectorScreen> {
  static const platform = MethodChannel('com.example.clock_widget/theme');
  String _currentTheme = 'bubble';

  Future<void> _setTheme(String theme) async {
    try {
      await platform.invokeMethod('setTheme', {'theme': theme});
      setState(() {
        _currentTheme = theme;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Theme set to $theme! Check your homescreen.')),
      );
    } on PlatformException catch (e) {
      print("Failed to set theme: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Select Widget Theme'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          _buildThemeCard('Pastel Bubbles', 'bubble', Icons.bubble_chart, const Color(0xFFE1BEE7)),
          _buildThemeCard('Green LED', 'digital', Icons.access_time, const Color(0xFF00C853)),
          _buildThemeCard('Flip Clock', 'flip', Icons.filter_9_plus, const Color(0xFF7B5E7B)),
          _buildThemeCard('Minimal', 'minimal', Icons.crop_din, Colors.black),
        ],
      ),
    );
  }

  Widget _buildThemeCard(String title, String themeId, IconData icon, Color color) {
    return Card(
      elevation: 4,
      margin: const EdgeInsets.only(bottom: 16),
      child: ListTile(
        leading: Icon(icon, color: color, size: 40),
        title: Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
        subtitle: const Text('Tap to apply this theme to the widget'),
        trailing: _currentTheme == themeId ? const Icon(Icons.check_circle, color: Colors.green) : null,
        onTap: () => _setTheme(themeId),
      ),
    );
  }
}
