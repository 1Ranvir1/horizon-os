# Signed developer attribution; private signing material is never packaged.
PRODUCT_COPY_FILES += \
    vendor/gtos/provenance/creator.json:$(TARGET_COPY_OUT_PRODUCT)/etc/horizon/creator.json \
    vendor/gtos/provenance/creator.sig:$(TARGET_COPY_OUT_PRODUCT)/etc/horizon/creator.sig \
    vendor/gtos/provenance/owner-public.pem:$(TARGET_COPY_OUT_PRODUCT)/etc/horizon/owner-public.pem
