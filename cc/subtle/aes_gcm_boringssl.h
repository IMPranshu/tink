// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
///////////////////////////////////////////////////////////////////////////////

#ifndef TINK_SUBTLE_AES_GCM_BORINGSSL_H_
#define TINK_SUBTLE_AES_GCM_BORINGSSL_H_

#include <memory>
#include <utility>

#include "absl/base/macros.h"
#include "absl/strings/string_view.h"
#include "openssl/evp.h"
#include "tink/aead.h"
#include "tink/internal/fips_utils.h"
#include "tink/internal/ssl_unique_ptr.h"
#include "tink/util/secret_data.h"
#include "tink/util/statusor.h"

namespace crypto {
namespace tink {
namespace subtle {

class AesGcmBoringSsl : public Aead {
 public:
  ABSL_DEPRECATED("Use AesGcmBoringSsl::New(const util::SecretData&) instead.")
  static crypto::tink::util::StatusOr<std::unique_ptr<Aead>> New(
      absl::string_view key_value) {
    return AesGcmBoringSsl::New(util::SecretDataFromStringView(key_value));
  }

  static crypto::tink::util::StatusOr<std::unique_ptr<Aead>> New(
      const util::SecretData& key);

  crypto::tink::util::StatusOr<std::string> Encrypt(
      absl::string_view plaintext,
      absl::string_view additional_data) const override;

  crypto::tink::util::StatusOr<std::string> Decrypt(
      absl::string_view ciphertext,
      absl::string_view additional_data) const override;

  static constexpr crypto::tink::internal::FipsCompatibility kFipsStatus =
      crypto::tink::internal::FipsCompatibility::kRequiresBoringCrypto;

 private:
#ifdef OPENSSL_IS_BORINGSSL
  explicit AesGcmBoringSsl(internal::SslUniquePtr<EVP_AEAD_CTX> context)
      : context_(std::move(context)) {}

  internal::SslUniquePtr<EVP_AEAD_CTX> context_;
#else
  explicit AesGcmBoringSsl(internal::SslUniquePtr<EVP_CIPHER_CTX> context)
      : context_(std::move(context)) {}

  // We cache the context to allow performing precomputations on the key.
  const internal::SslUniquePtr<EVP_CIPHER_CTX> context_;
#endif
};

}  // namespace subtle
}  // namespace tink
}  // namespace crypto

#endif  // TINK_SUBTLE_AES_GCM_BORINGSSL_H_
