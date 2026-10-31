FROM eclipse-temurin:25-resolute
RUN apt-get update

# libvips build requirements
RUN apt-get -y --no-install-suggests install -f build-essential ninja-build python3-pip bc wget
RUN apt-get -y --no-install-suggests install meson

# libvips image support libraries
RUN apt -y --no-install-suggests install \
    libfftw3-dev \
    libopenexr-dev `# OpenEXR (HDR) loading` \
    libgsf-1-dev \
    libglib2.0-dev \
    liborc-dev \
    libopenslide-dev \
    libmatio-dev \
    libwebp-dev `# WebP load/save` \
    libjpeg-turbo8-dev `# JPEG load/save` \
    libexpat1-dev \
    libexif-dev \
    libtiff5-dev `# TIFF load/save` \
    libcfitsio-dev \
    libpoppler-glib-dev `# PDF loading (rasterises pages)` \
    librsvg2-dev `# SVG loading` \
    libpango1.0-dev \
    libopenjp2-7-dev `# JPEG 2000 load/save` \
    liblcms2-dev \
    libimagequant-dev `# Palette quantisation for 8-bit PNG/GIF output (pngsave palette, Q, effort)` \
    libheif-dev `# HEIF/HEIC/AVIF load/save; codecs come from the plugins below` \
    libheif-plugin-libde265 `# HEVC decoding (reading HEIC)` \
    libheif-plugin-x265 `# HEVC encoding (writing HEIC)` \
    libheif-plugins-all `# Metapackage pulling in every libheif plugin; makes the individual plugins redundant` \
    libheif-plugin-aomdec `# AV1 decoding (reading AVIF)`

# build libvips from source
WORKDIR /tmp
RUN wget https://github.com/libvips/libvips/releases/download/v8.18.7/vips-8.18.7.tar.xz
RUN tar xf vips-8.18.7.tar.xz
WORKDIR /tmp/vips-8.18.7
RUN meson setup build
WORKDIR /tmp/vips-8.18.7/build
RUN meson compile
RUN meson test
RUN meson install
RUN ldconfig

RUN rm /tmp/vips-8.18.7.tar.xz
RUN rm -r /tmp/vips-8.18.7/

# libjemalloc to prevent memory fragmentation
RUN apt -y --no-install-suggests install \
  libjemalloc2

RUN apt -y --no-install-suggests install \
  graphicsmagick \
  graphicsmagick-imagemagick-compat \
  pngquant \
  libimage-exiftool-perl

