### 5. Public API You Plan to Use
- *NHTSA vPIC API* (https://vpic.nhtsa.dot.gov/api). It gives real vehicle make, model, and year data from the US government in JSON. No API key needed.
- *Example call:* https://vpic.nhtsa.dot.gov/api/vehicles/GetModelsForMake/honda?format=json
- We will cache the response so we do not call the API too much.
