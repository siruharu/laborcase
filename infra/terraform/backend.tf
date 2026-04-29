terraform {
  backend "gcs" {
    bucket = "laborcase-tfstate"
    prefix = "root"
  }
}
