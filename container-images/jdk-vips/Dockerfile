FROM eclipse-temurin:25-noble
RUN apt-get update
RUN apt-get -y --no-install-suggests install -f build-essential ninja-build  python3-pip bc wget
RUN apt-get -y --no-install-suggests install meson
RUN apt -y --no-install-suggests install \
    libfftw3-dev \
    libopenexr-dev \
    libgsf-1-dev \
    libglib2.0-dev \
    liborc-dev \
    libopenslide-dev \
    libmatio-dev \
    libwebp-dev \
    libjpeg-turbo8-dev \
    libexpat1-dev \
    libexif-dev \
    libtiff5-dev \
    libcfitsio-dev \
    libpoppler-glib-dev \
    librsvg2-dev \
    libpango1.0-dev \
    libopenjp2-7-dev \
    liblcms2-dev \
    libimagequant-dev

WORKDIR /tmp
RUN wget https://github.com/libvips/libvips/releases/download/v8.18.0/vips-8.18.0.tar.xz
RUN tar xf vips-8.18.0.tar.xz
WORKDIR /tmp/vips-8.18.0
RUN meson setup build
WORKDIR /tmp/vips-8.18.0/build
RUN meson compile
RUN meson test
RUN meson install
RUN ldconfig

RUN rm /tmp/vips-8.18.0.tar.xz
RUN rm -r /tmp/vips-8.18.0/

RUN apt -y --no-install-suggests install \
	pngquant \
	libimage-exiftool-perl \
	libjemalloc-dev

