#!/usr/bin/env ruby

# SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
# SPDX-License-Identifier: GPL-3.0-or-later

require 'pathname'
require 'yaml'

ROOT = Pathname.new(__dir__).parent.freeze

required_files = %w[
  CHANGELOG.md
  CODE_OF_CONDUCT.md
  CONTRIBUTING.md
  LICENSE.txt
  README.md
  SECURITY.md
  .github/CODEOWNERS
]

errors = required_files.filter_map do |relative|
  "missing required project file: #{relative}" unless ROOT.join(relative).file?
end

ROOT.glob('.github/ISSUE_TEMPLATE/*.yml').sort.each do |file|
  YAML.safe_load_file(file, aliases: false)
rescue Psych::SyntaxError => error
  errors << "invalid YAML in #{file.relative_path_from(ROOT)}: #{error.message}"
end

markdown_files = [
  *ROOT.glob('*.md'),
  ROOT.join('.github/PULL_REQUEST_TEMPLATE.md'),
  ROOT.join('docs/README.md'),
  *ROOT.glob('docs/opensw/*.md'),
].select(&:file?).uniq

markdown_files.each do |file|
  file.read.scan(/\[[^\]]+\]\(([^)]+)\)/).flatten.each do |raw_target|
    target = raw_target.strip.sub(/\A</, '').sub(/>\z/, '')
    next if target.match?(%r{\A(?:https?://|mailto:|#)})

    relative = target.split('#', 2).first
    next if relative.empty?

    resolved = file.dirname.join(relative).cleanpath
    next if resolved.exist?

    errors << "broken local link in #{file.relative_path_from(ROOT)}: #{target}"
  end
end

if errors.empty?
  puts "Project metadata valid: #{markdown_files.size} Markdown files and " \
       "#{ROOT.glob('.github/ISSUE_TEMPLATE/*.yml').size} issue forms checked"
  exit 0
end

warn errors.join("\n")
exit 1
