package com.gu.mediaservice.lib.cleanup

import com.gu.mediaservice.model.ImageMetadata

object PhotographerRenamer extends MetadataCleaner {
  val names = Map(
    "Abdullah Coskun" -> "Abdullah Coşkun",
    "Adam Warzawa" -> "Adam Warżawa",
    "Ahmet Izgi" -> "Ahmet İzgi",
    "Akos Stiller" -> "Ákos Stiller",
    "Albert Gonzalez Farran" -> "Albert González Farran",
    "Alberto E Rodriguez" -> "Alberto E Rodríguez",
    "Alberto Estevez" -> "Alberto Estévez",
    "Alberto Valdes" -> "Alberto Valdés",
    "Alecsandra Dragoi" -> "Alecsandra Raluca Drăgoi",
    "Alecsandra Raluca Dragoi" -> "Alecsandra Raluca Drăgoi",
    "Alejandro Garcia" -> "Alejandro García",
    "Aleksander Kozminski" -> "Aleksander Koźmiński",
    "Alex Caparros" -> "Álex Caparrós",
    "Alexandre Simoes" -> "Alexandre Simões",
    "Ali Balli" -> "Ali Ballı",
    "Ali Ihsan Ozturk" -> "Ali İhsan Öztürk",
    "Alik Keplicz" -> "Alik Kęplicz",
    "Alvaro Barrientos" -> "Álvaro Barrientos",
    "Alvin Baez" -> "Alvin Báez",
    "Amel Emric" -> "Amel Emrić",
    "Andre Liohn" -> "André Liohn",
    "Andre Penner" -> "André Penner",
    "Andreea Campeanu" -> "Andreea Câmpeanu",
    "Andrej Isakovic" -> "Andrej Isaković",
    "Andres Kudacki" -> "Andrés Kudacki",
    "Andres Martinez Casares" -> "Andrés Martínez Casares",
    "Angel Martinez" -> "Ángel Martínez",
    "Antonio Bronic" -> "Antonio Bronić",
    "Antonio Lacerda" -> "António Lacerda",
    "Arif Hudaverdi Yaman" -> "Arif Hüdaverdi Yaman",
    "Atilgan Ozdil" -> "Atılgan Özdil",
    "Attila Kovacs" -> "Attila Kovács",
    "Attila Volgyi" -> "Attila Völgyi",
    "Aurelien Meunier" -> "Aurélien Meunier",
    "Aykut Unlupinar" -> "Aykut Ünlüpınar",
    "Aytac Unal" -> "Aytaç Ünal",
    "Azael Rodriguez" -> "Azael Rodríguez",
    "Balazs Mohai" -> "Balázs Mohai",
    "Bartlomiej Zborowski" -> "Bartłomiej Zborowski",
    "Bartosz Banka" -> "Bartosz Bańka",
    "Behcet Alkan" -> "Behçet Alkan",
    "Benoit Bouchez" -> "Benoît Bouchez",
    "Benoit Doppagne" -> "Benoît Doppagne",
    "Benoit Goossens" -> "Benoît Goossens",
    "Benoit Jacques" -> "Benoît Jacques",
    "Benoit Pailley" -> "Benoît Pailley",
    "Benoit Peverelli" -> "Benoît Peverelli",
    "Benoit Peyrucq" -> "Benoît Peyrucq",
    "Benoit Tessier" -> "Benoît Tessier",
    "Benoite Fanton" -> "Benoîte Fanton",
    "Berk Ozkan" -> "Berk Özkan",
    "Bernadett Szabo" -> "Bernadett Szabó",
    "Bernat Armangue" -> "Bernat Armangué",
    "Biel Alino" -> "Biel Aliño",
    "Bilgin S Sasmaz" -> "Bilgin S Şaşmaz",
    "Bjorn Larsson Rosvall" -> "Björn Larsson Rosvall",
    "Boris Kovacev" -> "Boris Kovačev",
    "Brendan Mcdermid" -> "Brendan McDermid",
    "Bulent Kilic" -> "Bülent Kılıç",
    "Burak Cingi" -> "Burak Çıngı",
    "Burhan Ozbilici" -> "Burhan Özbilici",
    "Camilo Jose Vergara" -> "Camilo José Vergara",
    "Carlos Alvarez" -> "Carlos Álvarez",
    "Carlos Barria" -> "Carlos Barría",
    "Carlos Garcia Rawlins" -> "Carlos García Rawlins",
    "Carole Bellaiche" -> "Carole Bellaïche",
    "Cathal Mcnaughton" -> "Cathal McNaughton",
    "Cem Oksuz" -> "Cem Öksüz",
    "Cesar Manso" -> "César Manso",
    "Christof Koepsel" -> "Christof Köpsel",
    "Christophe Petit Tesson" -> "Christophe Petit-Tesson",
    "Cristobal Garcia" -> "Cristóbal García",
    "Cristobal Herrera" -> "Cristóbal Herrera",
    "Cristobal Osete" -> "Cristóbal Osete",
    "Cristobal Venegas" -> "Cristóbal Venegas",
    "Czarek Sokolowski" -> "Czarek Sokołowski",
    "Dado Ruvic" -> "Dado Ruvić",
    "Damir Sagolj" -> "Damir Šagolj",
    "Daniel Garcia" -> "Daniel García",
    "Daniel Mihailescu" -> "Daniel Mihăilescu",
    "Daniel Muñoz" -> "Daniel Muñoz",
    "Darko Bandic" -> "Darko Bandić",
    "Darko Vojinovic" -> "Darko Vojinović",
    "Dave M Benett" -> "David M Benett",
    "David Mcnew" -> "David McNew",
    "David W Cerny" -> "David W Černý",
    "David Wolff - Patrick" -> "David Wolff-Patrick",
    "Dawid Zuchowicz" -> "Dawid Żuchowicz",
    "Denis Lovrovic" -> "Denis Lovrović",
    "Diana Sanchez" -> "Diana Sánchez",
    "Don Mcphee" -> "Don McPhee",
    "Dusan Vranic" -> "Dušan Vranić",
    "Eduardo Munoz" -> "Eduardo Muñoz",
    "Eduardo Munoz Alvarez" -> "Eduardo Muñoz",
    "Egle Kazdailyte" -> "Eglė Každailytė",
    "Elif Ozturk" -> "Elif Öztürk",
    "Elin Hoyland" -> "Elin Høyland",
    "Emrah Gurel" -> "Emrah Gürel",
    "Ercin Top" -> "Erçin Top",
    "Erdem Sahin" -> "Erdem Şahin",
    "Eric Piermont" -> "Éric Piermont",
    "Erik Mcgregor" -> "Erik McGregor",
    "Esteban Felix" -> "Esteban Félix",
    "Etienne Chognard" -> "Étienne Chognard",
    "Etienne Laurent" -> "Étienne Laurent",
    "Eugene Garcia" -> "Eugene García",
    "Felix Kaestle" -> "Felix Kästle",
    "Feriq Ferec" -> "Feriq Fereç",
    "Francois Duhamel" -> "François Duhamel",
    "Francois Durand" -> "François Durand",
    "Francois G Durand" -> "François Durand",
    "Francois Guillot" -> "François Guillot",
    "Francois Lenoir" -> "François Lenoir",
    "Francois Lepage" -> "François Lepage",
    "Francois Lo Presti" -> "François Lo Presti",
    "Francois Mori" -> "François Mori",
    "Francois Nascimbeni" -> "François Nascimbeni",
    "Francois Nel" -> "François Nel",
    "Francois Pauletto" -> "François Pauletto",
    "Francois Walschaerts" -> "François Walschaerts",
    "Francois Xavier Marit" -> "François-Xavier Marit",
    "Francois-Xavier Marit" -> "François-Xavier Marit",
    "Gaelle Beri" -> "Gaëlle Beri",
    "Gerard Julien" -> "Gérard Julien",
    "Gergely Janossy" -> "Gergely Jánossy",
    "Goran Kovacic" -> "Goran Kovačić",
    "Goran Tomasevic" -> "Goran Tomašević",
    "Grosescu Alberto Mihai" -> "Alberto Groșescu",
    "Grzegorz Michalowski" -> "Grzegorz Michałowski",
    "Gyorgy Varga" -> "György Varga",
    "Hakan Burak Altunoz" -> "Hakan Burak Altunöz",
    "Hannah Mckay" -> "Hannah McKay",
    "Hector Guerrero" -> "Héctor Guerrero",
    "Hector Gutierrez" -> "Héctor Gutiérrez",
    "Hector Mata" -> "Héctor Mata",
    "Hector Retamal" -> "Héctor Retamal",
    "Helene Pambrun" -> "Hélène Pambrun",
    "Herika Martinez" -> "Hérika Martínez",
    "Huseyin Yildiz" -> "Hüseyin Yıldız",
    "Ian Macnicol" -> "Ian MacNicol",
    "Inti Ocon" -> "Inti Ocón",
    "Ints Kalnins" -> "Ints Kalniņš",
    "Isa Terli" -> "İsa Terli",
    "Ivan Alvarado" -> "Iván Alvarado",
    "Ivan Gabaldon" -> "Iván Gabaldón",
    "Jakub Gavlak" -> "Jakub Gavlák",
    "Jakub Kaminski" -> "Jakub Kamiński",
    "Janek Skarzynski" -> "Janek Skarżyński",
    "Javier Garcia" -> "Javier García",
    "Javier Lizon" -> "Javier Lizón",
    "Jean-Francois Badias" -> "Jean-François Badias",
    "Jean-Francois Monier" -> "Jean-François Monier",
    "Jean-Paul Pelissier" -> "Jean-Paul Pélissier",
    "Jean-Sebastien Evrard" -> "Jean-Sébastien Evrard",
    "Jens Schlueter " -> "Jens Schlüter",
    "Jerome Delay" -> "Jérôme Delay",
    "Jerome Favre" -> "Jérôme Favre",
    "Jerome Prebois" -> "Jérôme Prébois",
    "Jerome Prevost" -> "Jérôme Prévost",
    "Jerome Sessini" -> "Jérôme Sessini",
    "Jesus Diges" -> "Jesús Diges",
    "Jesus Serrano Redondo" -> "Jesús Serrano Redondo",
    "Joaquin Sarmiento" -> "Joaquín Sarmiento",
    "Johan Ordonez" -> "Johan Ordóñez",
    "John Macdougall" -> "John MacDougall",
    "John Vizcaino" -> "John Vizcaíno",
    "Jon Gustafsson" -> "Jón Gústafsson",
    "Jorg Carstensen" -> "Jörg Carstensen",
    "Jorge Dan Lopez" -> "Jorge Dan López",
    "Jorge Duenes" -> "Jorge Dueñes",
    "Jorge Saenz" -> "Jorge Sáenz",
    "Jose Cabezas" -> "José Cabezas",
    "Jose Cendon" -> "José Cendón",
    "Jose Fragozo" -> "José Fragozo",
    "Jose Fuste Raga" -> "José Fuste Raga",
    "Jose Giribas" -> "José Giribás",
    "Jose Jacome" -> "José Jácome",
    "Jose Jordan" -> "José Jordan",
    "Jose Luis Gonzalez" -> "José Luis González",
    "Jose Luis Magana" -> "José Luis Magaña",
    "Jose Luis Pelaez" -> "José Luis Peláez",
    "Jose Luis Saavedra" -> "José Luis Saavedra",
    "Jose Manuel Vidal" -> "José Manuel Vidal",
    "Jose Miguel Gomez" -> "José Miguel Gómez",
    "Jose Palazon" -> "José Palazón",
    "Jose Sena Goulao" -> "José Sena Goulão",
    "Juan Naharro Gimenez" -> "Juan Naharro Giménez",
    "Julio Cesar Aguilar" -> "Julio César Aguilar",
    "Kai Schwoerer" -> "Kai Schwörer",
    "Kamil Altiparmak" -> "Kamil Altıparmak",
    "Kamil Krzaczynski" -> "Kamil Krzaczyński",
    "Kayhan Ozer" -> "Kayhan Özer",
    "Kc Mcginnis" -> "KC McGinnis",
    "Kerim Okten" -> "Kerim Ökten",
    "Laszlo Balogh" -> "László Balogh",
    "Laurence Mathieu-Leger" -> "Laurence Mathieu-Léger",
    "Laurent Gillieron" -> "Laurent Gilliéron",
    "Laurentiu Garofeanu" -> "Laurențiu Garofeanu",
    "Lech Muszynski" -> "Lech Muszyński",
    "Leo Correa" -> "Léo Corrêa",
    "Leonardo Munoz" -> "Leonardo Muñoz",
    "Leonhard Foeger" -> "Leonhard Föger",
    "Leszek Szymanski" -> "Leszek Szymański",
    "Lise Aserud" -> "Lise Åserud",
    "Lluis Gene" -> "Lluís Gené",
    "Logan Mcmillan" -> "Logan McMillan",
    "Loic Aedo" -> "Loïc Aedo",
    "Loic Venance" -> "Loïc Venance",
    "Lokman Ilhan" -> "Lokman İlhan",
    "Lucio Tavora" -> "Lúcio Távora",
    "Lukasz Cynalewski" -> "Łukasz Cynalewski",
    "Lukasz Kalinowski" -> "Łukasz Kalinowski",
    "Lukasz Szelemej" -> "Łukasz Szełemej",
    "Maciej Kulczynski" -> "Maciej Kulczyński",
    "Maciek Jazwiecki" -> "Maciek Jaźwiecki",
    "Mahmut Serdar Alakus" -> "Mahmut Serdar Alakuş",
    "Manu Fernandez" -> "Manu Fernández",
    "Marc Mccormack" -> "Marc McCormack",
    "Marcelo Del Pozo" -> "Marcelo del Pozo",
    "Marcelo Sayao" -> "Marcelo Sayão",
    "Marcial Guillen" -> "Marcial Guillén",
    "Marcio Jose Sanchez" -> "Marcio José Sánchez",
    "Martin Divisek" -> "Martin Divíšek",
    "Mario Arturo Martinez" -> "Mario Arturo Martínez",
    "Mario Vazquez" -> "Mario Vázquez",
    "Marko Djurica" -> "Marko Đurica",
    "Marko Drobnjakovic" -> "Marko Drobnjaković",
    "Martin Mejia" -> "Martín Mejía",
    "Marton Monus" -> "Márton Mónus",
    "Mateusz Slodkowski" -> "Mateusz Słodkowski",
    "Matthias Schrader" -> "Matthias Schräder",
    "Mauricio Duenas Castaneda" -> "Mauricio Dueñas Castañeda",
    "Mehmet Emin Gurbuz" -> "Mehmet Emin Gürbüz",
    "Michal Cizek" -> "Michal Čížek",
    "Michelle Mcloughlin" -> "Michelle McLoughlin",
    "Miguel Gutierrez" -> "Miguel Gutiérrez",
    "Milos Bicanski" -> "Miloš Bičanski",
    "Miro Kuzmanovic" -> "Miro Kuzmanović",
    "Moises Castillo" -> "Moisés Castillo",
    "Morne de Klerk" -> "Morné de Klerk",
    "Mustafa Ciftci" -> "Mustafa Çiftçi",
    "Mustafa Yalcin" -> "Mustafa Yalçın",
    "Niklas Halle’N" -> "Niklas Halle’n",
    "Onur Coban" -> "Onur Çoban",
    "Oscar del Pozo" -> "Óscar del Pozo",
    "Osman Orsal" -> "Osman Örsal",
    "Ozan Kose" -> "Ozan Köse",
    "Ozge Elif Kizil" -> "Özge Elif Kızıl",
    "Ozkan Bilgin" -> "Özkan Bilgin",
    "P-M Heden" -> "P-M Hedén",
    "Pablo Blazquez Dominguez" -> "Pablo Blázquez Domínguez",
    "Pablo Martinez Monsivais" -> "Pablo Martínez Monsiváis",
    "Pal Hansen" -> "Pål Hansen",
    "Patricia de Melo Moreira" -> "Patrícia de Melo Moreira",
    "Paul Mcerlane" -> "Paul McErlane",
    "Pawel Jaszczuk" -> "Paweł Jaszczuk",
    "Pawel Kopczynski" -> "Paweł Kopczyński",
    "Petar Kujundzic" -> "Petar Kujundžić",
    "Peter Dejong" -> "Peter de Jong",
    "Pietro D’aprano" -> "Pietro D’Aprano",
    "Rade Prelic" -> "Rade Prelić",
    "Radek Mica" -> "Radek Miča",
    "Rafal Guz" -> "Rafał Guz",
    "Ramon Espinosa" -> "Ramón Espinosa",
    "Raul Arboleda" -> "Raúl Arboleda",
    "Raul Caro Cadenas" -> "Raúl Caro Cadenas",
    "Regis Duvignau" -> "Régis Duvignau",
    "Reinnier Kaze" -> "Reinnier Kazé",
    "Remi Chauvin" -> "Rémi Chauvin",
    "Remus Tiplea" -> "Remus Țiplea",
    "Remy de la Mauviniere" -> "Remy de la Mauvinière",
    "Remy Gabalda" -> "Rémy Gabalda",
    "Ricardo Mazalan" -> "Ricardo Mazalán",
    "Rodrigo Buendia" -> "Rodrigo Buendía",
    "Rolex Dela Pena" -> "Rolex dela Peña",
    "Romain Jacquet-Lagreze" -> "Romain Jacquet-Lagrèze",
    "Sanjin Strukic" -> "Sanjin Strukić",
    "Sashenka Gutierrez" -> "Sáshenka Gutiérrez",
    "Sebastian Tataru" -> "Sebastian Tătaru",
    "Sebastiao Moreira" -> "Sebastião Moreira",
    "Sebastiao Salgado " -> "Sebastião Salgado ",
    "Sebastien Bozon" -> "Sébastien Bozon",
    "Sebastien Martinet" -> "Sébastien Martinet",
    "Sebastien Nogier" -> "Sébastien Nogier",
    "Sebastien Salom Gomis" -> "Sébastien Salom-Gomis",
    "Sebastien Thibault" -> "Sébastien Thibault",
    "Sebnem Coskun" -> "Şebnem Coşkun",
    "Serkan Avci" -> "Serkan Avcı",
    "Sercan Kucuksahin" -> "Sercan Küçükşahin",
    "Sergio Morae" -> "Sérgio Morae",
    "Sergio Perez" -> "Sergio Pérez",
    "Sertac Kayar" -> "Sertaç Kayar",
    "Slavek Ruta" -> "Slávek Růta",
    "Slaven Vlasic" -> "Slaven Vlašić",
    "Srdjan Stevanovic" -> "Srđan Stevanović",
    "Srdjan Suki" -> "Srđan Suki",
    "Stanislaw Rozpedzik" -> "Stanisław Rozpędzik",
    "Stephane Cardinale" -> "Stéphane Cardinale",
    "Stephane de Sakutin" -> "Stéphane de Sakutin",
    "Stephane Mahe" -> "Stéphane Mahé",
    "Stephanie Lecocq" -> "Stéphanie Lecocq",
    "Swen Pfortner" -> "Swen Pförtner",
    "Szilard Koszticsak" -> "Szilárd Koszticsák",
    "Tamas Kaszas" -> "Tamás Kaszás",
    "Tamas Kovacs" -> "Tamás Kovács",
    "Tayfun Coskun" -> "Tayfun Coşkun",
    "Thilo Schmuelgen" -> "Thilo Schmülgen",
    "Thomas Niedermueller" -> "Thomas Niedermüller",
    "Tolga Bozoglu" -> "Tolga Bozoğlu",
    "Toms Kalnins" -> "Toms Kalniņš",
    "Tytus Zmijewski" -> "Tytus Żmijewski",
    "Umit Bektas" -> "Ümit Bektaş",
    "Vadim Ghirda" -> "Vadim Ghirdă",
    "Valda Kalnina" -> "Valda Kalniņa",
    "Valerie Gache" -> "Valérie Gache",
    "Valerie Macon" -> "Valérie Macon",
    "Valery Hache" -> "Valéry Hache",
    "Venturelli" -> "Daniele Venturelli",
    "Victor R Caivano" -> "Víctor R Caivano",
    "Vincent Perez" -> "Vincent Pérez",
    "Vit Simanek" -> "Vít Šimánek",
    "Vladimir Simicek" -> "Vladimír Šimíček",
    "Wiktor Dabkowski" -> "Wiktor Dąbkowski",
    "Wojciech Kruczynski" -> "Wojciech Kruczyński",
    "Wojtek Radwanski" -> "Wojtek Radwański",
    "Wojciech Strozyk" -> "Wojciech Stróżyk",
    "Yasin Akgul" -> "Yasin Akgül",
    "Yilmaz Kazandioglu" -> "Yılmaz Kazandıoğlu",
    "Yunus Emre Gunaydin" -> "Yunus Emre Günaydın",
    "Yuri Cortez" -> "Yuri Cortéz",
    "Zoltan Balogh" -> "Zoltán Balogh",
    "Zoltan Gergely Kelemen" -> "Zoltán Gergely Kelemen",
    "Zsolt Czegledi" -> "Zsolt Czeglédi",
    "Zvonimir Barisin" -> "Zvonimir Barišin"
  )

  override def clean(metadata: ImageMetadata): ImageMetadata = {
    metadata.copy(byline = metadata.byline.flatMap(names.get(_).orElse(metadata.byline)))
  }
}
