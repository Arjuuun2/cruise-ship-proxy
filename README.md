# cruise-ship-proxy

This project implements a proxy system that allows all HTTP/HTTPS traffic from the Royal Caribbean cruidse ship to travel through a single persistent TCP connection to reduce costs. 

The system consistes fo two components

1. **Ship Proxy (Client)** -
Runs on the ship and accepts HTTP/HTTPS requests from users 

2. **Offshore Proxy (Server)** -
Runs on land and makes actual HTTP/HTTPS requests to the internet


