package com.connor.nearestplane

/**
 * airplanes.live usually supplies a `desc` field ("BOEING 747-8"), and that's
 * preferred when present. This table is the fallback for feeds that omit it,
 * and it covers the types you'll realistically see overhead.
 */
object AircraftTypes {

    fun fullName(icaoType: String?): String? {
        if (icaoType.isNullOrBlank()) return null
        return TABLE[icaoType.uppercase()]
    }

    /** Title-cases the API's shouty `desc` field: "BOEING 737-800" -> "Boeing 737-800". */
    fun tidyDescription(desc: String?): String? {
        if (desc.isNullOrBlank()) return null
        return desc.trim().split(" ").joinToString(" ") { word ->
            if (word.any { it.isDigit() } || word.length <= 2) word.uppercase()
            else word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private val TABLE = mapOf(
        // Airbus narrowbody
        "A319" to "Airbus A319", "A320" to "Airbus A320", "A321" to "Airbus A321",
        "A19N" to "Airbus A319neo", "A20N" to "Airbus A320neo", "A21N" to "Airbus A321neo",
        "A318" to "Airbus A318",
        // Airbus widebody
        "A306" to "Airbus A300-600", "A310" to "Airbus A310",
        "A332" to "Airbus A330-200", "A333" to "Airbus A330-300",
        "A338" to "Airbus A330-800neo", "A339" to "Airbus A330-900neo",
        "A342" to "Airbus A340-200", "A343" to "Airbus A340-300",
        "A345" to "Airbus A340-500", "A346" to "Airbus A340-600",
        "A359" to "Airbus A350-900", "A35K" to "Airbus A350-1000",
        "A388" to "Airbus A380-800",
        // Boeing narrowbody
        "B712" to "Boeing 717-200",
        "B733" to "Boeing 737-300", "B734" to "Boeing 737-400", "B735" to "Boeing 737-500",
        "B736" to "Boeing 737-600", "B737" to "Boeing 737-700", "B738" to "Boeing 737-800",
        "B739" to "Boeing 737-900",
        "B37M" to "Boeing 737 MAX 7", "B38M" to "Boeing 737 MAX 8",
        "B39M" to "Boeing 737 MAX 9", "B3XM" to "Boeing 737 MAX 10",
        "B752" to "Boeing 757-200", "B753" to "Boeing 757-300",
        // Boeing widebody
        "B762" to "Boeing 767-200", "B763" to "Boeing 767-300", "B764" to "Boeing 767-400",
        "B772" to "Boeing 777-200", "B77L" to "Boeing 777-200LR",
        "B773" to "Boeing 777-300", "B77W" to "Boeing 777-300ER",
        "B778" to "Boeing 777-8", "B779" to "Boeing 777-9",
        "B788" to "Boeing 787-8", "B789" to "Boeing 787-9", "B78X" to "Boeing 787-10",
        "B741" to "Boeing 747-100", "B742" to "Boeing 747-200", "B743" to "Boeing 747-300",
        "B744" to "Boeing 747-400", "B748" to "Boeing 747-8", "B74S" to "Boeing 747SP",
        // Embraer
        "E135" to "Embraer ERJ-135", "E145" to "Embraer ERJ-145",
        "E170" to "Embraer E170", "E175" to "Embraer E175",
        "E190" to "Embraer E190", "E195" to "Embraer E195",
        "E290" to "Embraer E190-E2", "E295" to "Embraer E195-E2",
        "E50P" to "Embraer Phenom 100", "E55P" to "Embraer Phenom 300",
        "E545" to "Embraer Legacy 450", "E550" to "Embraer Legacy 500",
        // Bombardier / Mitsubishi
        "CRJ2" to "Bombardier CRJ200", "CRJ7" to "Bombardier CRJ700",
        "CRJ9" to "Bombardier CRJ900", "CRJX" to "Bombardier CRJ1000",
        "BCS1" to "Airbus A220-100", "BCS3" to "Airbus A220-300",
        "DH8A" to "De Havilland Dash 8-100", "DH8C" to "De Havilland Dash 8-300",
        "DH8D" to "De Havilland Dash 8-400",
        "CL30" to "Bombardier Challenger 300", "CL35" to "Bombardier Challenger 350",
        "CL60" to "Bombardier Challenger 600", "GL5T" to "Bombardier Global 5000",
        "GLEX" to "Bombardier Global Express", "GL7T" to "Bombardier Global 7500",
        // Business jets
        "C25A" to "Cessna Citation CJ2", "C25B" to "Cessna Citation CJ3",
        "C25C" to "Cessna Citation CJ4", "C500" to "Cessna Citation I",
        "C510" to "Cessna Citation Mustang", "C525" to "Cessna CitationJet",
        "C550" to "Cessna Citation II", "C560" to "Cessna Citation V",
        "C56X" to "Cessna Citation Excel", "C680" to "Cessna Citation Sovereign",
        "C68A" to "Cessna Citation Latitude", "C700" to "Cessna Citation Longitude",
        "C750" to "Cessna Citation X",
        "GLF4" to "Gulfstream IV", "GLF5" to "Gulfstream V",
        "GLF6" to "Gulfstream G650", "G280" to "Gulfstream G280",
        "FA7X" to "Dassault Falcon 7X", "FA8X" to "Dassault Falcon 8X",
        "F2TH" to "Dassault Falcon 2000", "F900" to "Dassault Falcon 900",
        "LJ35" to "Learjet 35", "LJ45" to "Learjet 45", "LJ60" to "Learjet 60",
        "H25B" to "Hawker 800",
        // Turboprops and GA
        "PC12" to "Pilatus PC-12", "PC24" to "Pilatus PC-24",
        "TBM7" to "Daher TBM 700", "TBM8" to "Daher TBM 850", "TBM9" to "Daher TBM 900",
        "BE20" to "Beechcraft King Air 200", "BE35" to "Beechcraft Bonanza",
        "BE36" to "Beechcraft Bonanza A36", "BE58" to "Beechcraft Baron",
        "B350" to "Beechcraft King Air 350",
        "C172" to "Cessna 172 Skyhawk", "C152" to "Cessna 152",
        "C182" to "Cessna 182 Skylane", "C206" to "Cessna 206 Stationair",
        "C208" to "Cessna 208 Caravan", "C210" to "Cessna 210 Centurion",
        "P28A" to "Piper PA-28 Cherokee", "P28R" to "Piper PA-28R Arrow",
        "PA31" to "Piper Navajo", "PA34" to "Piper Seneca", "PA46" to "Piper Malibu",
        "SR20" to "Cirrus SR20", "SR22" to "Cirrus SR22", "SF50" to "Cirrus Vision Jet",
        "DA40" to "Diamond DA40", "DA42" to "Diamond DA42", "DA62" to "Diamond DA62",
        "AT72" to "ATR 72", "AT75" to "ATR 72-500", "AT76" to "ATR 72-600",
        "AT43" to "ATR 42", "AT45" to "ATR 42-500",
        "SW4" to "Fairchild Metroliner", "SB20" to "Saab 2000", "SF34" to "Saab 340",
        // Cargo and military
        "MD11" to "McDonnell Douglas MD-11", "MD82" to "McDonnell Douglas MD-82",
        "MD83" to "McDonnell Douglas MD-83", "MD88" to "McDonnell Douglas MD-88",
        "DC10" to "McDonnell Douglas DC-10",
        "C130" to "Lockheed C-130 Hercules", "C30J" to "Lockheed C-130J Super Hercules",
        "C17" to "Boeing C-17 Globemaster III", "C5M" to "Lockheed C-5M Super Galaxy",
        "K35R" to "Boeing KC-135 Stratotanker", "KC46" to "Boeing KC-46 Pegasus",
        "E3TF" to "Boeing E-3 Sentry", "P8" to "Boeing P-8 Poseidon",
        "F16" to "General Dynamics F-16", "F15" to "McDonnell Douglas F-15",
        "F18S" to "Boeing F/A-18 Super Hornet", "F35" to "Lockheed Martin F-35",
        "A10" to "Fairchild A-10 Thunderbolt II", "T38" to "Northrop T-38 Talon",
        // Helicopters
        "EC35" to "Airbus H135", "EC45" to "Airbus H145", "EC30" to "Airbus H130",
        "AS50" to "Airbus AS350 Écureuil", "A139" to "Leonardo AW139",
        "A109" to "Leonardo AW109", "B06" to "Bell 206 JetRanger",
        "B407" to "Bell 407", "B412" to "Bell 412", "B429" to "Bell 429",
        "R44" to "Robinson R44", "R66" to "Robinson R66",
        "S76" to "Sikorsky S-76", "S92" to "Sikorsky S-92",
        "H60" to "Sikorsky UH-60 Black Hawk"
    )
}
