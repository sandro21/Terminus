CREATE TABLE IF NOT EXISTS stations (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    lines TEXT[] NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS gtfs_stops (
    stop_id TEXT PRIMARY KEY,
    stop_name TEXT NOT NULL,
    parent_station TEXT,
    location_type INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS gtfs_routes (
    route_id TEXT PRIMARY KEY,
    route_short_name TEXT NOT NULL,
    route_type INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS gtfs_trips (
    trip_id TEXT PRIMARY KEY,
    route_id TEXT NOT NULL,
    service_id TEXT NOT NULL,
    trip_headsign TEXT NOT NULL DEFAULT '',
    direction_id TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS gtfs_stop_times (
    trip_id TEXT NOT NULL,
    stop_id TEXT NOT NULL,
    stop_sequence INTEGER NOT NULL,
    departure_time TEXT NOT NULL,
    PRIMARY KEY (trip_id, stop_sequence)
);

CREATE TABLE IF NOT EXISTS gtfs_calendar (
    service_id TEXT PRIMARY KEY,
    monday BOOLEAN NOT NULL,
    tuesday BOOLEAN NOT NULL,
    wednesday BOOLEAN NOT NULL,
    thursday BOOLEAN NOT NULL,
    friday BOOLEAN NOT NULL,
    saturday BOOLEAN NOT NULL,
    sunday BOOLEAN NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS gtfs_calendar_dates (
    service_id TEXT NOT NULL,
    service_date DATE NOT NULL,
    exception_type INTEGER NOT NULL,
    PRIMARY KEY (service_id, service_date)
);

CREATE TABLE IF NOT EXISTS departure_index (
    station_id TEXT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    service_date DATE NOT NULL,
    departure_seconds INTEGER NOT NULL CHECK (departure_seconds >= 0),
    route_id TEXT NOT NULL,
    line TEXT NOT NULL,
    direction TEXT NOT NULL,
    destination TEXT NOT NULL,
    trip_id TEXT NOT NULL,
    PRIMARY KEY (station_id, service_date, trip_id, departure_seconds)
);

CREATE INDEX IF NOT EXISTS departure_station_time_idx
    ON departure_index(station_id, service_date, departure_seconds);

CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    fcm_token TEXT NOT NULL UNIQUE,
    platform TEXT NOT NULL DEFAULT 'android',
    valid BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS subscriptions (
    device_id TEXT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    station_id TEXT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    line TEXT NOT NULL,
    minimum_delay_seconds INTEGER NOT NULL DEFAULT 60,
    enabled BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id, station_id, line)
);

CREATE TABLE IF NOT EXISTS realtime_arrivals (
    station_id TEXT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    train_id TEXT NOT NULL,
    line TEXT NOT NULL,
    direction TEXT NOT NULL,
    destination TEXT NOT NULL,
    event_time TEXT NOT NULL,
    waiting_seconds INTEGER NOT NULL DEFAULT 0,
    delay_seconds INTEGER NOT NULL DEFAULT 0,
    is_realtime BOOLEAN NOT NULL DEFAULT false,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (station_id, train_id)
);

CREATE TABLE IF NOT EXISTS delay_notifications (
    device_id TEXT NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    train_id TEXT NOT NULL,
    station_id TEXT NOT NULL,
    delay_seconds INTEGER NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id, train_id, station_id)
);

INSERT INTO stations(id, name, lines) VALUES
('AIRPORT', 'Airport', ARRAY['RED','GOLD']),
('ARTS_CENTER', 'Arts Center', ARRAY['RED','GOLD']),
('ASHBY', 'Ashby', ARRAY['BLUE','GREEN']),
('AVONDALE', 'Avondale', ARRAY['BLUE']),
('BANKHEAD', 'Bankhead', ARRAY['GREEN']),
('BROOKHAVEN_OGLETHORPE', 'Brookhaven-Oglethorpe', ARRAY['GOLD']),
('BUCKHEAD', 'Buckhead', ARRAY['RED']),
('CHAMBLEE', 'Chamblee', ARRAY['GOLD']),
('CIVIC_CENTER', 'Civic Center', ARRAY['RED','GOLD']),
('COLLEGE_PARK', 'College Park', ARRAY['RED','GOLD']),
('DECATUR', 'Decatur', ARRAY['BLUE']),
('DORAVILLE', 'Doraville', ARRAY['GOLD']),
('DUNWOODY', 'Dunwoody', ARRAY['RED']),
('EAST_LAKE', 'East Lake', ARRAY['BLUE']),
('EAST_POINT', 'East Point', ARRAY['RED','GOLD']),
('EDGEWOOD_CANDLER_PARK', 'Edgewood-Candler Park', ARRAY['BLUE','GREEN']),
('FIVE_POINTS', 'Five Points', ARRAY['RED','GOLD','BLUE','GREEN']),
('GARNETT', 'Garnett', ARRAY['RED','GOLD']),
('GEORGIA_STATE', 'Georgia State', ARRAY['BLUE','GREEN']),
('GWCC_CNN_CENTER', 'GWCC/CNN Center', ARRAY['BLUE','GREEN']),
('HAMILTON_E_HOLMES', 'Hamilton E Holmes', ARRAY['BLUE']),
('INDIAN_CREEK', 'Indian Creek', ARRAY['BLUE']),
('INMAN_PARK_REYNOLDSTOWN', 'Inman Park-Reynoldstown', ARRAY['BLUE','GREEN']),
('KENSINGTON', 'Kensington', ARRAY['BLUE']),
('KING_MEMORIAL', 'King Memorial', ARRAY['BLUE','GREEN']),
('LAKEWOOD_FORT_MCPHERSON', 'Lakewood-Fort McPherson', ARRAY['RED','GOLD']),
('LENOX', 'Lenox', ARRAY['GOLD']),
('LINDBERGH_CENTER', 'Lindbergh Center', ARRAY['RED','GOLD']),
('MEDICAL_CENTER', 'Medical Center', ARRAY['RED']),
('MIDTOWN', 'Midtown', ARRAY['RED','GOLD']),
('NORTH_AVENUE', 'North Avenue', ARRAY['RED','GOLD']),
('NORTH_SPRINGS', 'North Springs', ARRAY['RED']),
('OAKLAND_CITY', 'Oakland City', ARRAY['RED','GOLD']),
('PEACHTREE_CENTER', 'Peachtree Center', ARRAY['RED','GOLD']),
('SANDY_SPRINGS', 'Sandy Springs', ARRAY['RED']),
('VINE_CITY', 'Vine City', ARRAY['BLUE','GREEN']),
('WEST_END', 'West End', ARRAY['RED','GOLD']),
('WEST_LAKE', 'West Lake', ARRAY['BLUE'])
ON CONFLICT (id) DO NOTHING;
