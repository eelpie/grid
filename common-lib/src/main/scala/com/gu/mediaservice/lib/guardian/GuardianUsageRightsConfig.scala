package com.gu.mediaservice.lib.guardian

import com.gu.mediaservice.lib.config.{PublicationPhotographers, UsageRightsConfigProvider}

object GuardianUsageRightsConfig extends UsageRightsConfigProvider {
  private val ObserverPublication = "The Observer"
  private val GuardianPublication = "The Guardian"

  val externalStaffPhotographers: List[PublicationPhotographers] = List(
    PublicationPhotographers(GuardianPublication, List(
      "Ben Doherty",
      "Bill Code",
      "Calla Wahlquist",
      "David Sillitoe",
      "Graham Turner",
      "Helen Davidson",
      "Jill Mead",
      //"Jonny Weeks", (Commented out as Jonny's photo's aren't always as Staff.)
      "Joshua Robertson",
      "Rachel Vere",
      "Roger Tooth",
      "Sean Smith",
      "Melissa Davey",
      "Michael Safi",
      "Michael Slezak",
      "Sean Smith",
      "Carly Earl",
      // Past
      "Dan Chung",
      "Denis Thorpe",
      "Don McPhee",
      "Frank Baron",
      "Frank Martin",
      "Garry Weaser",
      "Graham Finlayson",
      "Martin Argles",
      "Peter Johns",
      "Robert Smithies",
      "Tom Stuttard",
      "Tricia De Courcy Ling",
      "Walter Doughty",
      "Eric Wadsworth",
    )),
    PublicationPhotographers(ObserverPublication, List(
      "David Newell Smith",
      "Tony McGrath",
      "Catherine Shaw",
      "John Reardon",
      "Sean Gibson"
    ))
  )

  // these are people who aren't photographers by trade, but have taken photographs for us.
  // This is mainly used so when we ingest photos from Picdar, we make sure we categorise
  // them correctly.
  // TODO: Think about removin these once Picdar is dead.
  val internalStaffPhotographers = List(
    PublicationPhotographers(GuardianPublication, List(
      "E Hamilton West",
      "Harriet St Johnston",
      "Lorna Roach",
      "Rachel Vere",
      "Ken Saunders"
    ))
  )

  val contractedPhotographers = List(
    PublicationPhotographers(ObserverPublication, List(
      "Andy Hall",
      "Antonio Olmos",
      "Gary Calton",
      "Jane Bown",
      "Jonathan Lovekin",
      "Karen Robinson",
      "Katherine Anne Rose",
      "Sophia Evans",
      "Suki Dhanda"
    )),
     PublicationPhotographers(GuardianPublication, List(
      "Alicia Canter",
      "Antonio Olmos",
      "Christopher Thomond",
      "David Levene",
      "Eamonn McCabe",
      "Graeme Robertson",
      "Johanna Parkin",
      "Linda Nylind",
      "Louise Hagger",
      "Martin Godwin",
      "Mike Bowers",
      "Murdo MacLeod",
      "Richard Saker",
      "Sarah Lee",
      "Tom Jenkins",
      "Tristram Kenton",
      "Jill Mead",
    ))
 )

  val staffIllustrators = List(
    "Guardian Design"
  )

  val contractIllustrators = List(
    PublicationPhotographers(GuardianPublication, List(
      "Ben Lamb",
      "Andrzej Krauze",
      "David Squires",
      "First Dog on the Moon",
      "Harry Venning",
      "Martin Rowson",
      "Matt Kenyon",
      "Matthew Blease",
      "Nicola Jennings",
      "Rosalind Asquith",
      "Steve Bell",
      "Steven Appleby",
      "Ben Jennings",
    )),
    PublicationPhotographers(ObserverPublication, List(
      "Chris Riddell",
      "David Foldvari",
      "David Simonds",
    ))
  )

  val creativeCommonsLicense = List(
    "CC BY-4.0", "CC BY-SA-4.0", "CC BY-ND-4.0"
  )

  /* These are currently hardcoded */
  val payGettySourceList = List(
    /* Chelsea possibly temporary, until Putin’s gone */
    "Chelsea FC",
    "ABC News",
    "AFPTV",
    "Alinari",
    "Arnold Newman Collection",
    "Baim Collection",
    "Barrett-Jackson",
    "Bob Thomas Sports Photography",
    "Catwalking",
    "CBS Television Stations Group RR",
    "Contour",
    "Contour RA",
    "Corbis Premium Historical",
    "Editorial Specials",
    "First Freedom",
    "Gamma-Legends",
    "Getty Images Sport Classic",
    "Icon Sport",
    "J.LEAGUE",
    "KBC - Japan",
    "Klaud9",
    "Kyodo News",
    "Kyodo News Stills",
    "Lichfield Studios Limited",
    "Lonely Planet RF",
    "Major League Baseball Platinum",
    "Mondadori Portfolio Premium",
    "NBA Classic",
    "NBC News Archives Clips",
    "Neil Leifer Collection",
    "Newspix",
    "NHK Video Bank Creative",
    "NHK Video Bank Editorial",
    "NHK Video Bank Premium",
    "PA Images",
    "Papixs",
    "Paris Match Archive",
    "Pele 10",
    "Popperfoto",
    "Premium Archive",
    "Premium Archive Films Editorial",
    "Reportage Archive",
    "SAMURAI JAPAN",
    "SNS Group",
    "Sports Illustrated",
    "Sports Illustrated Classic",
    "Storyful",
    "Sygma Premium",
    "The Asahi Shimbun Premium",
    "The Asahi Shimbun Video",
    "ullstein bild Premium",
    "Ulrich Baumgarten",
    "Vision Media",
    // Here goes the list of inactive collections too
    "#girlgaze",
    "2VISTA",
    "360cities.net Editorial",
    "360cities.net RM",
    "3D4Medical.com",
    "3DClinic",
    "40260 RF",
    "ABSODELS RM",
    "ACP",
    "Action Plus",
    "Aflo Foto Agency RM",
    "AFP Creative",
    "age fotostock RM",
    "Alaska Stock Images RF",
    "Alaska Stock Images RR",
    "Alaskan Express RF",
    "All Canada Photos RM",
    "Allsport Concepts",
    "Altrendo",
    "Altrendo RR",
    "amana images RM",
    "America 24-7",
    "arabianEye RM",
    "Arcaid Images",
    "Arcaid RR",
    "Arcangel Images RR",
    "Archive Photos Creative",
    "Arena Football League",
    "Aridi",
    "Art Images",
    "Artville",
    "ASAblanca",
    "Asia Images RF",
    "Asia Images RM",
    "Astrakan",
    "Aurora",
    "Aurora Plus",
    "Author's Image RF",
    "AWL Images RM",
    "Axiom Photographic Agency",
    "Barcroft",
    "Barcroft Media",
    "Bettmann Creative",
    "bilderlounge RR",
    "Biosphoto RM",
    "Black Box",
    "Blend Images RM",
    "Blend Images RR",
    "Bloomberg Creative Photos RM",
    "Boost",
    "Botanica",
    "Broadway.com RM",
    "Built.Images RF",
    "Caiaimage",
    "Canopy RM",
    "Car Culture",
    "Cavan Images RM",
    "CBS Watch Magazine",
    "CCN Images RR",
    "CGIBackgrounds",
    "Check Six",
    "Chic Sketch Editorial",
    "Chic Sketch RF",
    "China Span RM",
    "Christian Science Monitor",
    "CI BuzzFoto",
    "CI Europa Press",
    "CI FilmMagic",
    "CI FilmMagic, Inc",
    "CI FM Europa Press",
    "CI Getty Images Entertainment",
    "CI Getty Images Sport",
    "CI News Feature",
    "CI WI Europa Press",
    "CI WireImage",
    "Citizen Stock RM",
    "Clerkenwell",
    "clipart.com",
    "Code Red",
    "Codex",
    "Collection Mix Subjects RM",
    "Collection Vogue Paris",
    "Collegiate Images",
    "Colorsport",
    "Comstock Images",
    "Conde Nast Collection Editorial",
    "Conde Nast Collection RM",
    "Conde Nast Lifestyle Collection",
    "Construction Photography RF",
    "Contour Style",
    "Contour Style Creative",
    "Corbis Documentary",
    "Corbis Historical Creative",
    "Corbis NX",
    "Corbis RF",
    "Corbis RM Stills",
    "Cote",
    "CSA Images RM",
    "Cuboimages RM",
    "Cultura Exclusive",
    "Cultura RF",
    "Cultura RM",
    "Cusp RM",
    "Custom Medical Stock Photo RF",
    "Custom Medical Stock Photo RM",
    "Cut and Deal RF",
    "Da Vinci Codex Atlanticus",
    "Daily Express",
    "DAJ",
    "DAJ RM",
    "Dave Benett Library",
    "De Agostini RM",
    "Denkou RF",
    "DigitalGlobe",
    "Discovery Channel Images RM",
    "DK Stock",
    "Dorling Kindersley",
    "Eastphoto RF",
    "Eastphoto RM",
    "Ecoscene RR",
    "El Universal",
    "Emotive Images RF",
    "ESTADÃO CONTEÚDO",
    "Everyday Projects",
    "Eye Ubiquitous RR",
    "EyeEm RM",
    "Eyewire",
    "EyeWire Other",
    "F1online RM",
    "Fame Flynet Stills",
    "Fancy RF",
    "Federugby",
    "Fever Images RF",
    "Feyenoord",
    "Finanzen Verlag",
    "First Light",
    "Flickr Flash",
    "Flickr Prime",
    "Flickr State",
    "Flirt RF",
    "FM Europa Press",
    "FogStock",
    "Folio Images RF",
    "Folio Images RM",
    "FoodPix",
    "FoodShapes RF",
    "Fototrove",
    "Fox Entertainment Group",
    "Gallo Images",
    "Gamma-Features",
    "GAP Photos RM",
    "Garden Picture Library RM",
    "Genuine Japan Creative Stills",
    "Genuine Japan Editorial Stills",
    "George Steinmetz",
    "Getty Images - NASCAR Partners",
    "Getty Images Special Access",
    "Global Cricket Ventures - BCCI",
    "Globo",
    "Glow RM",
    "Glowimages RM",
    "GoGo Images RF",
    "GoodSalt RR",
    "GraphEast RF",
    "GraphEast RM",
    "Gulf Images RM",
    "Hemera",
    "Hemis.fr RM",
    "Her Og Nu",
    "Hero Images",
    "Hero Images Corbis",
    "HillCreek Pictures RF",
    "Historic Map Works",
    "Hoberman Collection UK RR",
    "Hola Images RM",
    "Hoxton",
    "I Love Images RF",
    "Iconic Images",
    "Iconica",
    "Iconotec RF",
    "Ikon Images",
    "Illustration Works",
    "Image Farm RF",
    "Image Ideas RF",
    "Image Partner Media",
    "Image100",
    "imageBROKER RM",
    "ImageDJ RF",
    "Imagemore",
    "ImageRite RF",
    "Images Bazaar",
    "Images.com RF",
    "imageshop RF",
    "imagesouk",
    "ImageState RF",
    "ImageState RM",
    "Imagezoo RM",
    "ImaZinS RM",
    "Index Stock Images RR",
    "Indian Premier League",
    "Inmagineasia",
    "InsideOutPix RF",
    "Inspirestock RF",
    "Interact Images",
    "International Speedway Corp.",
    "Iromaya RF",
    "IS Stock RF",
    "iStock Exclusive RF",
    "iStock Main",
    "iStock Signature",
    "iStock Signature Plus",
    "iStock Vectors Plus",
    "ItaliaStock",
    "JLPGA",
    "John Warburton-Lee RR",
    "Johner Images",
    "Jon Arnold Images RF",
    "JTB Photo RM",
    "Juice Images RF",
    "Juniors Bildarchiv RM",
    "Kablonk RF",
    "Kallista Images",
    "Keith Levit Photography RF",
    "Keystone RF",
    "Kobal Collection",
    "Las Vegas Stock RR",
    "LAT",
    "LatinContent RM",
    "Laughing Stock RM",
    "Lifesize",
    "Link Image RM",
    "London Stills RR",
    "Lonely Planet Images",
    "LOOK",
    "LuckyPix RR",
    "Luxy",
    "Map Resources",
    "Mary Evans Picture Library RM",
    "Masterfile",
    "Masters",
    "mauritius images RM",
    "Mayo Clinic Collection",
    "MedioImages",
    "Melba Photo Agency RF",
    "Mike King",
    "Minden Pictures II",
    "Minden Pictures RM",
    "Mint Images RM",
    "Mise En Beaute RR",
    "MLBPA - The Players Choice",
    "Moment RM",
    "Moment Select",
    "Moment Unreleased",
    "Mondadori Portfolio",
    "National Geographic",
    "National Geographic Magazines",
    "National Geographic RF",
    "Nativestock",
    "Nature Picture Library",
    "Neovision RM",
    "Nettavisen",
    "New York Cosmos",
    "newstockimages RF",
    "NFL",
    "Nordic Life",
    "Nordic Photos",
    "NucleusMedicalArt.com RM",
    "NYonAir",
    "Oceans-Image RR",
    "Offside Live",
    "OJO Images RM",
    "OJO Plus RF",
    "Old Visuals RF",
    "Olive Images RF",
    "Open Door Images RF",
    "Open Mike Productions",
    "Oxford Scientific RM",
    "Pacific Stock RM",
    "PANAPRESS",
    "PanoramaStock RF",
    "Panoramic Images RM",
    "Panoramic Images RR",
    "Panther Media RF",
    "Paris Match Collection",
    "Passage RM",
    "Perspectives",
    "Peter Arnold",
    "Photo Exchange Bank Germany",
    "PhotoAlto Agency RM",
    "Photodisc",
    "Photographer's Choice",
    "Photographer's Choice RR",
    "Photolibrary RF",
    "Photolibrary RM",
    "Photonica",
    "Photonica World",
    "Photononstop RM",
    "Phototake RM",
    "Phovoir RF",
    "Picture Press RM",
    "Pixmann RF",
    "Pixta",
    "Popperfoto Creative",
    "Popstar Pictures",
    "Popular Science",
    "Portsmouth FC",
    "Premium Ent",
    "Private Label",
    "Publisher Mix RM",
    "Queerstock",
    "QuickImage RF",
    "QuickImage RR",
    "Radius Images RF",
    "Rainer Schlegelmilch",
    "Real Latino RF",
    "Realistic Reflections",
    "Red Cover RM",
    "Redlink RM",
    "Refinery29 RM",
    "relaximages",
    "Reportage by Getty Images",
    "Retrofile",
    "Reunion Images",
    "Riser",
    "Robert Harding World Imagery",
    "RooM RM",
    "SAKIstyle RM",
    "SambaPhoto",
    "Science Faction",
    "Science Faction Jewels",
    "Science Photo Library RM",
    "Science Source",
    "ScienceFoto RM",
    "Scoopt",
    "Sebun",
    "simple stock shots RF",
    "Sites & Photos",
    "Smart.MAGNA RF",
    "Snapwi.re",
    "SodaStyle",
    "SoFood Collection RF",
    "Solus",
    "Somos RF",
    "Sony BMG Music Entertainment",
    "SPL Creative RM",
    "Sport Plus",
    "Starface Image Collection",
    "Stock Illustration RF",
    "Stock Illustration Source",
    "Stock4B",
    "stockbrokerXtra RF",
    "Stockbyte",
    "Stockbyte Global",
    "stockbyway RF",
    "Stockdisc",
    "StockFood Creative RM",
    "StockFood Creative RR",
    "StockImage",
    "Stone",
    "Studio Harcourt",
    "SuperStock RF",
    "SuperStock RM",
    "Swimwear by Popstar",
    "Tango Stock RM",
    "TAO Images RM",
    "TASS",
    "Taxi",
    "Taxi Japan RM",
    "Televisa",
    "Terry O'Neill",
    "TF-Images",
    "the Agency Collection",
    "The Axel Springer Collection",
    "The England Collection",
    "The Gruner & Jahr Collection",
    "The Image Bank",
    "The LIFE Images Collection",
    "The LIFE Picture Collection",
    "The LIFE Premium Collection",
    "The New York Post",
    "The Stock Connection RR",
    "The StockPile Collection RF",
    "Thinkstock",
    "Tim de Waele",
    "Tohoku Colour Agency RM",
    "TongRo Images RF",
    "Topic Images",
    "Triangle",
    "Trond Tandberg",
    "Twenty20 RF",
    "Universal Images Group",
    "Untitled X-Ray",
    "Uppercut RM",
    "Urban CGI RF",
    "View Stock RM",
    "VII",
    "VII Premium",
    "VisitBritain RF",
    "VisitBritain RM",
    "Visual China Group Video",
    "Visual Language RF",
    "Visuals Unlimited",
    "Warner Bros. Entertainment",
    "WaterFrame RM",
    "Wembley National Stadium Ltd",
    "West Ham United FC",
    "Westend61 RM",
    "WI Europa Press",
    "WIN-Initiative RM",
    "Workbook Stock",
    "World Kabbadi League",
    "World Sport Group",
    "Yann Arthus-Bertrand",
    "Zefa RF",
    "Zen Shui RF"
  )

  val freeSuppliers = List(
    "AAP",
    "Alamy",
    "Allstar Picture Library",
    "AP",
    "EPA",
    "Getty Images",
    "PA",
    "Reuters",
    "Rex Features",
    "Ronald Grant Archive",
    "Action Images",
  )

  val suppliersCollectionExcl = Map(
    "Getty Images" -> payGettySourceList
  )

}
