require "minitest/autorun"
require_relative "../beta_delivery"

class BetaDeliveryTest < Minitest::Test
  Group = Struct.new(:name, :is_internal_group)

  def info
    { "CFBundleIdentifier" => "com.lalilu.lmusic", "CFBundleShortVersionString" => "1.0",
      "CFBundleVersion" => "103", "ITSAppUsesNonExemptEncryption" => false }
  end

  def test_wrong_build_cannot_be_distributed
    assert_raises(RuntimeError) { BetaDelivery.identity(info, "104") }
    assert_equal "103", BetaDelivery.identity(info, "103")[:build]
  end

  def test_export_compliance_is_not_guessed
    assert_raises(RuntimeError) { BetaDelivery.identity(info.reject { |key, _| key == "ITSAppUsesNonExemptEncryption" }, "103") }
  end

  def test_internal_group_or_missing_beta_fails_before_upload
    assert_raises(RuntimeError) { BetaDelivery.external_group([Group.new("Beta", true)]) }
    assert_raises(RuntimeError) { BetaDelivery.external_group([Group.new("Alpha", false)]) }
    beta = Group.new("Beta", false)
    assert_same beta, BetaDelivery.external_group([Group.new("Alpha", true), beta])
  end

  def test_waiting_review_is_not_reported_as_installable
    assert_match "not yet installable", BetaDelivery.status("WAITING_FOR_BETA_REVIEW", true)
    assert_match "available", BetaDelivery.status("IN_BETA_TESTING", true)
  end

  def test_unlinked_or_unsubmitted_build_is_not_success
    assert_raises(RuntimeError) { BetaDelivery.status("IN_BETA_TESTING", false) }
    assert_raises(RuntimeError) { BetaDelivery.status("READY_FOR_BETA_SUBMISSION", true) }
    assert_raises(RuntimeError) { BetaDelivery.status("BETA_REJECTED", true) }
  end
end
