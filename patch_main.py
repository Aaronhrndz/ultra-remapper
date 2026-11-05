with open('C:/temp/ultra-remapper/app/src/main/java/com/aaronhrndz/ultraremapper/MainActivity.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# Replace home gesture rows
text = text.replace(
    'Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)',
    'Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee())'
)
text = text.replace(
    'Text(currentLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)',
    'Text(currentLabel, maxLines = 1, modifier = Modifier.basicMarquee())'
)

# Replace Settings button in Home
old_settings_btn = """                                Text(
                                    text = context.getString(R.string.settings_title),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = context.getString(R.string.settings_subtitle),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(0xFF9EA7B8),
                                    style = MaterialTheme.typography.labelSmall
                                )"""

new_settings_btn = """                                Text(
                                    text = context.getString(R.string.settings_title),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = context.getString(R.string.settings_subtitle),
                                    maxLines = 1,
                                    color = Color(0xFF9EA7B8),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.basicMarquee()
                                )"""

text = text.replace(old_settings_btn, new_settings_btn)

# Replace app picker list label
old_app_label = """                        Text(
                            app.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )"""

new_app_label = """                        Text(
                            app.label,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )"""

text = text.replace(old_app_label, new_app_label)

# Replace update details
old_upd_label = """                        Text(
                            context.getString(R.string.update_install_watch),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )"""

new_upd_label = """                        Text(
                            context.getString(R.string.update_install_watch),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )"""

text = text.replace(old_upd_label, new_upd_label)

text = text.replace(
    'Text(context.getString(R.string.update_install_watch_sub), maxLines = 1, overflow = TextOverflow.Ellipsis)',
    'Text(context.getString(R.string.update_install_watch_sub), maxLines = 1, modifier = Modifier.basicMarquee())'
)

# Replace AdbHelp buttons
old_help_btns = """                        Text(
                            context.getString(R.string.diag_dialog_go_to_settings),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )"""

new_help_btns = """                        Text(
                            context.getString(R.string.diag_dialog_go_to_settings),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().basicMarquee()
                        )"""

text = text.replace(old_help_btns, new_help_btns)

old_close_btn = """                        Text(
                            context.getString(R.string.diag_dialog_close),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )"""

new_close_btn = """                        Text(
                            context.getString(R.string.diag_dialog_close),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().basicMarquee()
                        )"""

text = text.replace(old_close_btn, new_close_btn)

with open('C:/temp/ultra-remapper/app/src/main/java/com/aaronhrndz/ultraremapper/MainActivity.kt', 'w', encoding='utf-8', newline='\n') as f:
    f.write(text)

print("MainActivity texts updated to basicMarquee")
