# Keep validation independent of Fastlane so failures can be checked without credentials.
module BetaDelivery
  def self.identity(info, expected_build)
    identifier, version, build = info.values_at("CFBundleIdentifier", "CFBundleShortVersionString", "CFBundleVersion")
    raise "Unexpected IPA bundle ID: #{identifier}" unless identifier == "com.lalilu.lmusic"
    raise "IPA version/build is missing" if [version, build].any? { |value| value.to_s.empty? }
    if expected_build && build.to_s != expected_build.to_s
      raise "IPA build #{build} does not match this CI run #{expected_build}"
    end
    raise "IPA must declare export compliance" unless [true, false].include?(info["ITSAppUsesNonExemptEncryption"])
    { identifier: identifier, version: version.to_s, build: build.to_s }
  end

  def self.external_group(groups)
    matches = groups.select { |group| group.name == "Beta" && group.is_internal_group == false }
    raise "Expected exactly one existing Beta external group, found #{matches.length}" unless matches.length == 1
    matches.first
  end

  def self.status(state, linked)
    raise "Build was not associated with the Beta group" unless linked
    case state
    when "WAITING_FOR_BETA_REVIEW", "IN_BETA_REVIEW"
      "submitted; awaiting Apple review (not yet installable)"
    when "IN_BETA_TESTING"
      "available to external testers"
    when "BETA_APPROVED", "READY_FOR_BETA_TESTING"
      "approved; automatic tester notification configured"
    else
      raise "Beta distribution incomplete: #{state.inspect}"
    end
  end
end
