# IFT3913 – Tâche 2 : tests générés par IA et analyse de mutation sur Apache Tika

# **Équipe :** Thomas Ring, Vianney Landry Ntebou Soutsing
# **Classe étudiée :** `org.apache.tika.utils.XMLReaderUtils` (module `tika-core`)
# **Outils :** ChatUniTest (modèle `code-llama` exécuté localement avec Ollama), pitest 1.17.4, JUnit 5, JDK 17



---

## 1. Classe choisie et justification

Nous avons retenu une seule classe, `XMLReaderUtils`, qui centralise la création et le recyclage des parseurs XML (DOM, SAX, StAX) de Tika. Elle a déjà un fichier de tests (`XMLReaderUtilsTest`, 6 tests), mais ces tests ne la couvrent que partiellement.

Résultats de pitest avec **les tests originaux seulement** :

| Mesure | Valeur |
|---|---|
| Couverture de lignes | 48 % (208 / 435) |
| Mutants générés | 180 |
| Mutants tués | 21 (couverture de mutation de 12 %) |
| Mutants couverts par au moins un test | 104 (force des tests de 20 %) |
| Mutants sans aucune couverture | 76 |
| Mutants couverts mais survivants | 83 |

Il y a donc 159 mutants vivants (76 sans couverture et 83 survivants), ce qui fait de la classe un bon candidat. Les méthodes visées pour la génération sont `getAttrValue` et `getMaxEntityExpansions` : les tests de Tika ne les appellent pas directement. Par exemple, le mutant « valeur de retour remplacée par `""` » de `getAttrValue` était dans l'état `NO_COVERAGE`. Le mutant « remplacement de int return par  `0` de `getMaxEntityExpansions` était dans l'état `NO_COVERAGE`.

Essai préalable : ChatUniTest a aussi été lancé sur `NoOpFilter`, mais l'outil a ignoré la méthode `doFilter`. La classe a donc été abandonnée au profit de `XMLReaderUtils`.

---

## 2. Pipeline : ChatUniTest et pitest dans Maven

Tout se passe dans `tika-core/pom.xml`.

**ChatUniTest.** Le plugin appelle un modèle `code-llama` servi par Ollama sur la machine locale (aucune API externe). Les points de configuration utiles :
- JDK 17 obligatoire, `dependencyManagement` pour imposer une version de `byte-buddy` compatible.
- Sous Windows avec WSL, Ollama écoute sur `0.0.0.0:11434` et l'URL du plugin utilise l'adresse IP de WSL, qui change à chaque redémarrage de Windows.
- La dépendance `chatunitest-starter` a été placée en portée `test`, avec des exclusions (`junit:junit`, `junit-vintage-engine`, `junit-platform-runner`). Sans cela, elle apportait JUnit 4 et des versions de la plateforme JUnit incompatibles : surefire choisissait le fournisseur JUnit 4 et exécutait **0 test**.

Dans `tika-core/pom.xml`, plugin de génération :

```xml
<plugin>
  <groupId>io.github.zju-aces-ise</groupId>
  <artifactId>chatunitest-maven-plugin</artifactId>
  <version>2.1.1</version>
  <configuration>
    <apiKeys>ollama</apiKeys>
    <model>code-llama</model>
    <url>http://<ip-de-wsl>:11434/v1/chat/completions</url>
    <maxPromptTokens>4096</maxPromptTokens>
    <thread>false</thread>
  </configuration>
</plugin>
```

L'adresse `<ip-de-wsl>` est l'adresse de la machine qui sert Ollama sur le réseau local (elle change selon la machine et au redémarrage). La clé `ollama` est une valeur factice : Ollama n'en demande pas.

Dans `tika-core/pom.xml`, dépendance de test (portée `test` et exclusions de JUnit 4) :

```xml
<dependency>
  <groupId>io.github.ZJU-ACES-ISE</groupId>
  <artifactId>chatunitest-starter</artifactId>
  <version>1.4.0</version>
  <type>pom</type>
  <scope>test</scope>
  <exclusions>
    <exclusion>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.junit.vintage</groupId>
      <artifactId>junit-vintage-engine</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.junit.platform</groupId>
      <artifactId>junit-platform-runner</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

Dans `tika-parent/pom.xml`, `dependencyManagement` : versions de `byte-buddy` et `byte-buddy-agent` épinglées à `1.17.7`, compatibles avec Mockito et ChatUniTest.

**pitest.** Plugin `pitest-maven` 1.17.4 avec `pitest-junit5-plugin` 1.2.1 (nécessaire pour détecter les tests JUnit 5), ciblant `XMLReaderUtils`. La configuration est dans `tika-core/pom.xml`, dans `<build><plugins>` :

```xml
<targetClasses><param>org.apache.tika.utils.XMLReaderUtils</param></targetClasses>
<targetTests><param>org.apache.tika.utils.*</param></targetTests>
<outputFormats><param>HTML</param><param>XML</param></outputFormats>
```

**Commandes.**

```
mvn clean test -pl tika-core
mvn clean test-compile org.pitest:pitest-maven:mutationCoverage -pl tika-core
```

Le rapport HTML est dans `tika-core/target/pit-reports/`. Les deux commandes réussissent sans aucune option de contournement (`-Dcheckstyle.skip` n'est pas utilisé).

---

## 3. Génération des tests et critique

**Où sont les tests générés ?** ChatUniTest écrit ses fichiers dans `tika-core/chatunitest-tests/org/apache/tika/utils/`, un dossier que Maven ne compile pas. Ces fichiers originaux sont conservés tels quels dans le dépôt. Ils ont été copiés dans `tika-core/src/test/java/org/apache/tika/utils/` pour être exécutés :

- `XMLReaderUtils_getAttrValue_35_0_Test`
- `XMLReaderUtils_getMaxEntityExpansions_33_0_Test`

chatUnitest n'a donc produit que 2 tests.

**Compilent-ils et passent-ils sans intervention ?** Oui pour la logique. La seule correction nécessaire a été de remplacer les imports avec `*` (par exemple `org.mockito.*`), que la règle Checkstyle de Tika interdit : 8 violations réparties sur les deux fichiers, corrigées en imports explicites sans modifier le code des tests. Le plugin Checkstyle de Tika (phase `validate`, avec `includeTestSourceDirectory` à `true` et `failOnViolation` à `true`) analyse aussi les tests : les violations bloquaient donc le build avant l'exécution de tout test. Chaque fichier contient un seul test, soit 2 tests de plus (749 → 751).

### Critique de `XMLReaderUtils_getAttrValue_35_0_Test`

Le test simule un objet `Attributes` avec Mockito (un seul attribut, `name` → `value`) puis vérifie que `getAttrValue("name", atts)` retourne `"value"`.

- **Point positif :** l'oracle est spécifique (valeur exacte, pas un simple `assertNotNull`), et le test atteint une méthode que les tests de Tika n'appelaient pas.
- **Limite :** un seul cas, le chemin nominal. Aucun test de l'attribut absent, de plusieurs attributs ou d'un objet vide.
- **Mock inutile :** `Attributes` a une implémentation réelle dans le JDK (`AttributesImpl`). Les `when(...)` décrivent le fonctionnement interne de la méthode (quels appels elle fait) plutôt que son comportement, ce qui rend le test fragile.
- **Extension inutile :** `@ExtendWith(MockitoExtension.class)` n'apporte rien puisque le mock est créé à la main.
- **Nom peu parlant :** `testGetAttrValue` ne dit pas ce qui est vérifié.

### Critique de `XMLReaderUtils_getMaxEntityExpansions_33_0_Test`

Le test appelle `getMaxEntityExpansions()` par réflexion sur une instance de la classe, puis vérifie que le résultat est positif et égal à `DEFAULT_MAX_ENTITY_EXPANSIONS`.

- **Réflexion inutile :** la méthode est publique et statique.
- **Mock jamais utilisé :** le champ `@Mock` est écrasé dans `setUp()` par `new XMLReaderUtils()`.
- **Oracle presque tautologique :** le test compare le getter à la constante qui initialise le champ. `assertTrue(... > 0)` est déjà impliqué par l'égalité.
- **Dépendance à l'état statique :** le test échoue si un autre test a modifié la valeur avant lui. L'aller-retour setter/getter, plus informatif, n'est pas testé.

### Comparaison avec les oracles écrits à la main

| | Tests de Tika (`XMLReaderUtilsTest`) | Tests générés | Tests manuels (section 6) |
|---|---|---|---|
| Portée | Parsing complet DOM, SAX, StAX | Une méthode, cas nominal | Une méthode ou un état précis |
| Oracle | Contenu du résultat, exceptions attendues (« billion laughs ») | Valeur exacte ou égalité avec une constante | Valeur exacte, état des pools, événements SAX |
| Cas limites | Oui (entités malveillantes) | Non | Oui (absent, vide, taille 0, valeur négative) |
| Défauts | Peu de couverture des méthodes utilitaires | Mocks inutiles, réflexion, dépendance à l'état statique | Réflexion nécessaire pour atteindre des champs privés |

---

## 4. Résultats de la mutation

Chaque ligne est un rapport pitest distinct, lancé après l'ajout du groupe de tests indiqué.

| Étape | Lignes | Mutation | Force des tests |
|---|---|---|---|
| Tests originaux de Tika | 48 % (208/435) | 12 % (21/180) | 20 % (21/104) |
| + 2 tests générés par ChatUniTest | 49 % (213/435) | 14 % (25/180) | 23 % (25/109) |
| + tests manuels : `getAttrValue`, `getTransformerFactory`, `trySetTransformerAttribute` | 52 % | 15 % | 23 % |
| + tests manuels : `buildPoolParser`, `clearReader`, `getDocumentBuilder(ParseContext)` | 55 % | 27 % | 40 % |
| + tests manuels : `buildDOM`, `parseSAX`, pool, `setPoolSize`, `trySetXercesSecurityManager`, StAX | **74 %** (322/435) | **56 %** (101/180) | **65 %** (101/155) |

Dans le rapport final, 79 mutants sur 180 restent vivants : 25 ne sont couverts par aucun test et 54 sont couverts mais survivent. Ils sont analysés à la section 7.

Les tests générés ne détectent pas tous les mutants, loin de là : ils font passer le score de mutation de 12 % à 14 %.

---

## 5. Mutants détectés par les tests générés

Entre le rapport « avant » et le rapport « après les tests générés », on observe :
- 5 lignes de plus couvertes (208 → 213),
- 5 mutants de plus couverts (104 → 109),
- **4 mutants de plus tués** (21 → 25).

Les quatre mutants tués en plus (obtenus en comparant les fichiers `mutations.xml` des deux rapports) :

| Ligne | Méthode | Mutant | Test qui le détecte | Pourquoi il est détecté |
|---|---|---|---|---|
| 1032 | `getAttrValue` | Condition de la boucle `for` niée | `XMLReaderUtils_getAttrValue_35_0_Test` | Le test fournit un attribut. Avec la condition niée, la boucle ne s'exécute pas, la méthode retourne `null` et `assertEquals("value", ...)` échoue. |
| 1033 | `getAttrValue` | Comparaison du nom local niée | `XMLReaderUtils_getAttrValue_35_0_Test` | Le nom demandé (`"name"`) correspond au nom local de l'attribut. Avec la comparaison niée, l'attribut est ignoré, le résultat est `null` et l'assertion échoue. |
| 1034 | `getAttrValue` | Valeur de retour remplacée par `""` | `XMLReaderUtils_getAttrValue_35_0_Test` | Le test attend `"value"` : `""` est différent. |
| 1005 | `getMaxEntityExpansions` | Valeur de retour remplacée par `0` | `XMLReaderUtils_getMaxEntityExpansions_33_0_Test` | Le test exige une valeur strictement positive et égale à la valeur par défaut. `0` viole les deux assertions. |

Ces quatre mutants ont un point commun : ils sont **grossiers**. Il suffit qu'un test appelle la méthode sur un cas nominal et compare le résultat à une valeur précise (et non à `assertNotNull`) pour les tuer. C'est ce que font les deux tests générés, malgré leurs défauts (section 3). L'oracle de `getAttrValue`, spécifique, détecte trois mutants à lui seul. L'oracle de `getMaxEntityExpansions`, presque tautologique, en détecte un seul, grâce à son `assertTrue(... > 0)`.

Les tests générés ne détectent pas plus parce qu'ils ne couvrent qu'un cas nominal sur deux méthodes très courtes, alors que la classe compte 180 mutants, dont la plupart se trouvent dans la gestion des pools de parseurs. Ils laissent aussi vivants les mutants des chemins d'erreur de `getAttrValue` (attribut absent, liste vide), que seuls les tests manuels de la section 6.1 exercent.

**Le cinquième mutant couvert mais non tué** (104 → 109 couverts, 21 → 25 tués) est le mutant de frontière de la ligne 1032 de `getAttrValue` : `i < atts.getLength()` devenu `i <= atts.getLength()`. Il survit parce que, dans le test généré, l'attribut cherché est trouvé à l'indice 0 : la méthode retourne avant d'atteindre la frontière. Plus généralement, ce mutant est probablement **équivalent** : la spécification de `org.xml.sax.Attributes` fait retourner `null` à `getLocalName(i)` quand l'indice est hors limites. L'itération supplémentaire compare donc `null` au nom cherché, ce qui est faux, et la boucle s'arrête sans changer le résultat.

**Un mutant resté sans couverture** dans le rapport « après les tests générés » : à la ligne 1037, la valeur de retour du `return null` final de `getAttrValue` remplacée par `""` (statut `NO_COVERAGE`). Le test généré ne teste que le cas où l'attribut est trouvé, donc il n'atteint jamais ce retour. Les tests manuels `getAttrValueReturnsNullWhenAttributeAbsent` et `getAttrValueReturnsNullOnEmptyAttributes` (section 6.1) ciblent précisément ce chemin. Dans le rapport final, ce mutant est bien passé à `KILLED`. Seul le mutant de frontière de la ligne 1032 survit encore dans `getAttrValue`, ce qui est cohérent avec l'hypothèse d'un mutant équivalent : les tests de l'attribut absent et de la liste vide atteignent la frontière sans que le résultat change.

---

## 6. Tests manuels

Tous les tests sont dans `tika-core/src/test/java/org/apache/tika/utils/XMLReaderUtilsManualTest.java` (40 tests). Plusieurs méthodes ciblées sont privées ou dépendent d'un état statique partagé (les pools). Les tests utilisent donc la réflexion, de faux objets (`FakeFactory`, `FakeSAXParser`, `FakeDbf`, `CountingBuilder`) et un utilitaire `withPool(...)` qui restaure toujours la taille du pool et le nombre maximal de réutilisations dans un `finally`.

Dans les tableaux, le document de test est `<a><b>x</b><b/></a>` (un élément racine, deux éléments `b`, soit 3 événements `startElement`).

### 6.1 `getAttrValue(String, Attributes)`

Mutants visés : valeur de retour remplacée par `""`, négation de la comparaison, frontière de boucle.

| Test | Intention | Données choisies | Oracle |
|---|---|---|---|
| `getAttrValueReturnsValueOfFirstAttribute` | La valeur de l'attribut demandé est retournée | Un seul attribut `id="abc"` | `"abc"`, valeur fournie en entrée |
| `getAttrValueFindsAttributeAfterOthers` | La boucle avance et trouve un attribut qui n'est pas le premier | Trois attributs `a=1`, `b=2`, `c=3` ; on demande `c` puis `b` | `"3"` puis `"2"` |
| `getAttrValueReturnsNullWhenAttributeAbsent` | Un attribut absent donne `null` | `id` présent, on demande `other` | `null` (retour final de la méthode) |
| `getAttrValueReturnsNullOnEmptyAttributes` | Aucune itération sur une liste vide | `AttributesImpl` vide | `null` |
| `getAttrValueMatchesLocalNameNotQualifiedName` | La comparaison porte sur le nom local, pas sur le nom qualifié | URI `urn:x`, nom local `name`, nom qualifié `x:name` | `"v"` pour `"name"`, `null` pour `"x:name"` |

### 6.2 `getTransformerFactory()` et `trySetTransformerAttribute`

Mutants visés : appels à `trySetTransformerAttribute` supprimés, exceptions relancées ou avalées.

| Test | Intention | Données choisies | Oracle |
|---|---|---|---|
| `getTransformerFactoryIsSecured` | La fabrique est configurée en mode sécurisé | Appel direct, sans données | Non nulle ; `FEATURE_SECURE_PROCESSING` vrai ; `ACCESS_EXTERNAL_DTD` et `ACCESS_EXTERNAL_STYLESHEET` égaux à `""` (valeurs posées par la méthode) |
| `trySetPassesAttributeAndValueThrough` | Le nom et la valeur sont transmis à la fabrique | Fausse fabrique qui mémorise les appels ; `"attr"` / `"val"` | `lastName == "attr"` et `lastValue == "val"` |
| `trySetRethrowsSecurityException` | Une `SecurityException` ne doit jamais être avalée | Fausse fabrique qui lève `SecurityException` | L'appel par réflexion lève une `InvocationTargetException` dont la cause est une `SecurityException` |
| `trySetSwallowsOtherExceptions` | Toute autre exception est journalisée, pas relancée | Fausse fabrique qui lève `IllegalArgumentException` | Aucune exception |
| `trySetSwallowsAbstractMethodError` | Même comportement pour un analyseur XML trop ancien | Fausse fabrique qui lève `AbstractMethodError` | Aucune exception |

### 6.3 `getDocumentBuilder(ParseContext)` et `clearReader`

Mutants visés : retours `null`, négation de la condition, appels de `setContentHandler`, `setDTDHandler`, `setEntityResolver`, `setErrorHandler` supprimés.

| Test | Intention | Données choisies | Oracle |
|---|---|---|---|
| `getDocumentBuilderReturnsBuilderFromContext` | Le builder du contexte est retourné tel quel | Builder placé dans un `ParseContext` | Même instance (`assertSame`) |
| `getDocumentBuilderCreatesOneWhenContextIsEmpty` | Sans builder dans le contexte, un nouveau est créé | `ParseContext` vide | Résultat non nul |
| `clearReaderReplacesAllFourHandlers` | Les quatre gestionnaires sont remplacés par des gestionnaires neutres | `XMLReader` d'un vrai parseur, avec le même `DefaultHandler` installé aux quatre endroits | Chaque getter retourne un objet non nul et différent de celui installé |
| `clearReaderAcceptsNull` | `null` est toléré | `null` | Aucune exception |

### 6.4 `buildPoolParser` (construction d'un parseur SAX pour le pool)

Mutants visés : retours `null`, négations des conditions, appels `reset` et `setProperty` supprimés, journal limité à une fois toutes les 5 minutes.

| Test | Intention | Données choisies | Oracle |
|---|---|---|---|
| `jdkParserGivesBuiltInPoolParser` | Un parseur du JDK donne un parseur « intégré » | Vrai `SAXParser` du JDK | Classe `BuiltInPoolSAXParser` |
| `parserWithoutResetIsUnrecognized` | Sans `reset`, le parseur n'est pas reconnu | Faux parseur dont `reset` lève `UnsupportedOperationException` | Classe `UnrecognizedPoolSAXParser` |
| `parserWithoutEntityLimitPropertyIsUnrecognized` | Sans propriété de limite d'entités, le parseur n'est pas reconnu | Faux parseur dont `setProperty` lève `SAXNotRecognizedException` | Classe `UnrecognizedPoolSAXParser` |
| `entityExpansionLimitIsSetOnParser` | La limite d'expansion est posée sur le parseur | Faux parseur qui mémorise ses propriétés | La propriété `entityExpansionLimit` vaut `getMaxEntityExpansions()` |
| `unsupportedLimitWarningIsThrottled` | L'avertissement n'est journalisé qu'une fois par période de 5 minutes | `LAST_LOG` forcé à 0, propriété non supportée, deux appels à 5 ms d'écart | `LAST_LOG` > 0 après le premier appel, inchangé après le second |

### 6.5 `buildDOM` et `parseSAX` (quatre et deux surcharges)

Mutants visés : retours `null`, appels `releaseDOMBuilder` et `releaseParser` supprimés, `parse` supprimé, négation de `poolBuilder != null`.

| Test | Intention | Données choisies | Oracle |
|---|---|---|---|
| `buildDOMFromStreamReturnsDocumentAndReleasesBuilder` | Document correct et builder rendu au pool | Pool de taille 2, flux de texte | Racine `a`, 2 éléments `b`, file de builders de taille 2 |
| `buildDOMFromReaderReturnsDocumentAndReleasesBuilder` | Même chose avec un `Reader` | Pool de taille 2, `StringReader` | Mêmes vérifications |
| `buildDOMFromPathAndUriReturnDocument` | Surcharges `Path` et URI | Fichier temporaire (`@TempDir`) | Document correct, pool de taille 2 après chaque appel |
| `buildDOMFallsBackWhenPoolIsEmpty` | Pool vide : un nouveau builder est créé | File de builders vidée par réflexion | Document correct, file toujours vide |
| `buildDOMUsesBuilderFromContext` | Le builder du contexte est utilisé | Builder « compteur » dans le `ParseContext` | `parses == 1`, pool intact (taille 2) |
| `parseSAXFromStreamDeliversEventsAndReleasesParser` | Événements reçus et parseur rendu | Pool de taille 2, compteur d'éléments | 3 événements ; file de parseurs de taille 2 |
| `parseSAXFromReaderDeliversEventsAndReleasesParser` | Même chose avec un `Reader` | Mêmes données | Mêmes vérifications |
| `parseSAXFallsBackWhenPoolIsEmpty` | Pool vide : un nouveau parseur est créé | File de parseurs vidée par réflexion | 3 événements, file vide |
| `parseSAXUsesParserFromContext` | Le parseur du contexte est utilisé | Faux parseur qui compte les appels à `getXMLReader` | Compteur > 0, 3 événements, pool intact |

### 6.6 Pool de parseurs : réutilisation, taille, verrous

Mutants visés : `incrementUses` supprimé, frontière `>=` de `MAX_NUM_REUSES`, `clear()` supprimé, retours `0` des accesseurs, `unlock()` supprimé.

| Test | Intention | Données choisies | Oracle |
|---|---|---|---|
| `saxParserIsRecycledThenReplacedAtMaxReuses` | Un parseur est recyclé, puis remplacé au nombre maximal d'utilisations | Pool de taille 1, `MAX_NUM_REUSES = 2`, deux parsings | Après le 1er : même objet en tête de file ; après le 2e : objet différent |
| `setPoolSizeRebuildsBothPools` | Le pool est reconstruit à la nouvelle taille | Taille 3 | `getPoolSize() == 3` ; files SAX et DOM de taille 3 |
| `poolSizeZeroEmptiesPoolsAndStillParses` | Taille 0 : pas de pool, mais le parsing fonctionne | Taille 0 | Files vides ; document DOM correct ; 3 événements SAX |
| `setPoolSizeRejectsNegativeValues` | Une taille négative est refusée | `-1` | `IllegalArgumentException` |
| `maxNumReusesRoundTrip` | Le setter et le getter sont cohérents | `7` | Le getter retourne 7 ; valeur d'origine restaurée dans un `finally` |
| `locksAreReleasedAfterUse` | Les verrous de lecture sont libérés après usage | `buildDOM`, `parseSAX` puis `setPoolSize` dans le même fil, délai maximal de 10 s | Pas de blocage (`assertTimeoutPreemptively`) : un `unlock()` supprimé rendrait le verrou d'écriture inaccessible |

### 6.7 Limites d'entités : `trySetXercesSecurityManager` (DOM et SAX) et `trySetStaxSecurityManager`

Mutants visés : appels `setAttribute`, `setProperty` supprimés, journal limité dans le temps, repli sur la propriété Woodstox.

| Test | Intention | Données choisies | Oracle |
|---|---|---|---|
| `dbfEntityExpansionLimitAttributeIsSet` | La limite est posée sur la fabrique DOM | Fausse `DocumentBuilderFactory` qui mémorise ses attributs | L'attribut `entityExpansionLimit` vaut `getMaxEntityExpansions()` |
| `dbfUnsupportedLimitIsSwallowedAndLogThrottled` | Un refus de l'attribut est avalé et journalisé une fois | Fausse fabrique qui refuse l'attribut, deux appels | `LAST_LOG` mis à jour au 1er appel, inchangé au 2e |
| `saxParserEntityExpansionLimitPropertyIsSet` | La limite est posée sur le parseur SAX | Faux `SAXParser` qui mémorise ses propriétés | La propriété vaut `getMaxEntityExpansions()` |
| `saxParserUnsupportedLimitIsSwallowedAndLogThrottled` | Même chose pour un refus côté SAX | Faux parseur qui refuse la propriété | Même vérification de `LAST_LOG` |
| `staxFallsBackToWoodstoxPropertyWhenJdkOneIsRejected` | Repli sur la propriété de Woodstox | Mock de `XMLInputFactory` qui rejette la propriété du JDK | `setProperty("com.ctc.wstx.maxEntityCount", getMaxEntityExpansions())` appelé |
| `staxSwallowsFailureOfBothProperties` | Si les deux propriétés échouent, l'erreur est avalée et un drapeau d'avertissement est levé | Mock qui rejette tout ; `HAS_WARNED_STAX` mis à `false` au départ | Drapeau à `true` après l'appel (valeur d'origine restaurée) |

---

## 7. Mutants non tués et analyse

Le rapport final compte **79 mutants vivants** sur 180 : 25 `NO_COVERAGE` (aucun test ne les atteint) et 54 `SURVIVED` (atteints, mais aucune assertion n'échoue). Cette liste vient du fichier `mutations.xml` du rapport final.

Pitest compte comme détectés les 6 mutants au statut `TIMED_OUT` (lignes 690, 740, 765, 816, 984 et 999 : un appel `unlock()` supprimé) : le verrou n'est jamais libéré, les tests se bloquent, et le délai maximal les interrompt. Ils sont inclus dans les 101 mutants détectés.

| # | Catégorie | Nb | Lignes | Statut | Peut-on les tuer ? |
|---|---|---|---|---|---|
| 1 | Code dépendant de Xerces | 14 | 832, 839 (×3), 876, 884 (×3), 1055, 1065 (×3), 1092, 1094 | `NO_COVERAGE` | Non, sans changer les dépendances du module |
| 2 | Méthodes qu'aucun test n'appelle | 11 | 184, 185, 396, 435 à 438, 1280, 1300, 1301, 1304 | `NO_COVERAGE` | Oui, faute de temps |
| 3 | Configuration de sécurité des fabriques | 25 | 205, 229 à 236, 259 à 270, 288, 289, 317, 344, 358, 369, 415, 416 | `SURVIVED` | En partie |
| 4 | Choix « pool ou nouvel objet » dans `buildDOM` et `parseSAX` | 10 | 461, 465, 476, 497, 501, 546, 550, 577, 653, 657 | `SURVIVED` | Presque tous équivalents |
| 5 | Recyclage du pool | 9 | 692, 713, 719, 720 (×2), 732, 767, 784, 808 | `SURVIVED` | En partie |
| 6 | Redimensionnement du pool | 3 | 968, 974, 993 | `SURVIVED` | Équivalents |
| 7 | Journal limité dans le temps | 3 | 856, 900, 1081 | `SURVIVED` | Équivalents |
| 8 | Avertissement StAX | 1 | 917 | `SURVIVED` | Équivalent |
| 9 | Frontière de la boucle de `getAttrValue` | 1 | 1032 | `SURVIVED` | Équivalent |
| 10 | Conditions de `buildPoolParser` | 2 | 1091, 1093 | `SURVIVED` | Équivalents sans Xerces |

### Explications par catégorie

**1. Code dépendant de Xerces (14 mutants).** Les lignes 832, 839, 876 et 884 se trouvent dans la boucle qui charge la classe `XERCES_SECURITY_MANAGER` par `Class.forName`, et les lignes 1055 et 1065 dans le bloc équivalent de `buildPoolParser`. Cette classe n'est pas dans le classpath de `tika-core` : une règle Maven du projet interdit d'ailleurs Xerces. L'exécution sort toujours par `ClassNotFoundException` avant ces lignes. Les retours des lignes 1092 et 1094 (parseurs « Xerces ») ne sont atteints que si un gestionnaire de sécurité Xerces a pu être installé. Les tuer demanderait d'ajouter Xerces aux dépendances de test, ce qui changerait le comportement de tout le module pour quelques mutants.

**2. Méthodes qu'aucun test n'appelle (11 mutants).** Ce sont `getXMLReader` (lignes 184 et 185), les deux surcharges de `getTransformer` (396, 1300, 1301, 1304), `getSAXTransformerFactory` (435 à 438) et `getXMLInputFactory` (1280). Ces mutants sont tuables avec des tests simples.

**3. Configuration de sécurité des fabriques (25 mutants).** Ce sont des appels supprimés qui configurent les fabriques de parseurs : `setNamespaceAware`, `setValidating`, `setExpandEntityReferences`, `trySetSAXFeature`, `trySetXercesSecurityManager`, `setEntityResolver`, `setErrorHandler`, etc. Les tests actuels vérifient le résultat des parsings, jamais l'état de la fabrique, donc supprimer un de ces appels ne fait échouer aucune assertion. Ils seraient tuables en testant l'état des objets (`isNamespaceAware()`, `getFeature(...)`) ou, mieux, un comportement (un document avec une entité externe doit être refusé). Les mutants des lignes 415 et 416 (`trySetTransformerAttribute`) survivent malgré le test `getTransformerFactoryIsSecured`. C'est cohérent avec l'hypothèse que la fabrique du JDK, en mode de traitement sécurisé, a déjà ces deux attributs à `""` : le mutant serait alors équivalent pour cette implémentation.

**4. Choix « pool ou nouvel objet » (10 mutants).** Dans chaque surcharge de `buildDOM` et `parseSAX`, les conditions `builder == null` et `POOL_SIZE == 0` choisissent entre un objet pris dans le pool et un objet neuf. Les nier change la provenance de l'objet, pas le document produit ni les événements reçus : ces mutants sont équivalents du point de vue de l'observateur. Fait exception le mutant de la ligne 476 (appel à `releaseDOMBuilder` supprimé dans une des surcharges), dont la survie n'a pas été élucidée.

**5. Recyclage du pool (9 mutants).**
- Lignes 713 et 784 : appels `reset` supprimés. Un objet recyclé sans `reset` donne le même résultat dans les cas testés.
- Lignes 719 et 720 (×2) : `incrementUses` supprimé et condition `numUses >= MAX_NUM_REUSES` du côté DOM. Le test équivalent du côté SAX existe et tue les mutants correspondants. Son pendant DOM a été retiré : lors du diagnostic, le compteur `numUses` du builder restait à 0 après deux parsings et le même objet restait en tête de file. La cause n'a pas été élucidée.
- Lignes 692, 732, 767 et 808 : conditions qui ne protègent qu'un avertissement (`builder == null`, `!success`). Les nier ne change que le journal.

**6. Redimensionnement du pool (3 mutants).** La ligne 968 supprime un `reset` avant de vider la file (même raisonnement qu'en 5). Aux lignes 974 et 993, la frontière `i < poolSize` devient `<=` : la file est bornée à `poolSize` et refuse le dernier ajout, donc le résultat observable est identique.

**7. Journal limité dans le temps (3 mutants).** Aux lignes 856, 900 et 1081, la comparaison `>` devient `>=`. Les deux comportements ne diffèrent que si l'heure courante vaut exactement `LAST_LOG` plus 5 minutes, à la milliseconde près : aucun test déterministe ne peut l'atteindre.

**8. Avertissement StAX (1 mutant).** À la ligne 917, la condition `HAS_WARNED_STAX.getAndSet(true) == false` est niée. Le drapeau est mis à `true` dans les deux cas, seul l'affichage de l'avertissement change.

**9. Frontière de la boucle de `getAttrValue` (1 mutant).** Voir la section 5 : un indice hors limites donne `null` d'après la spécification de `Attributes`.

**10. Conditions de `buildPoolParser` (2 mutants).** Aux lignes 1091 et 1093, la condition est une conjonction dont une partie dépend de `hasSecurityManager`, toujours faux sans Xerces. Nier l'autre partie ne change donc pas le résultat de la conjonction.

### Pour aller plus loin

Les mutants des catégories 2 et 3 sont les plus intéressants : ce sont 36 mutants (20 % du total) que des tests supplémentaires pourraient tuer, en excluant les mutants probablement équivalents des lignes 415 et 416. Les autres catégories sont soit équivalentes, soit dépendantes de Xerces.


---

## 8. Exécution

Tous les tests ajoutés passent avec la commande standard du projet, sans option de contournement. Le résumé de Maven indique 2 tests ignorés : ils appartiennent à Tika (`TikaInputStreamTest.reproduceRandomizedTestFailure` et `CustomErrorHandlerTest.testUndeclaredEntityXML`). Aucun des tests ajoutés n'est désactivé.

