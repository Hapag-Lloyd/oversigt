import { Injectable } from '@angular/core';
import { ToastrService } from 'ngx-toastr';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  constructor(
    private toastr: ToastrService, // https://www.npmjs.com/package/ngx-toastr
  ) { }

  info(message: string) {
    this.toastr.info(message);
  }

  error(message: string) {
    this.toastr.error(message);
  }

  success(message: string) {
    this.toastr.success(message);
  }

  warning(message: string) {
    this.toastr.warning(message);
  }
}
