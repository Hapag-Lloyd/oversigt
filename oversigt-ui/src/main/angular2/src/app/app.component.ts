import { Component, OnInit } from '@angular/core';
import { Configuration } from 'src/oversigt-client';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'oversigt-ui';
  isCollapsed: boolean;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private configuration: Configuration
  ) {
  }

  ngOnInit(): void {
    // tslint:disable-next-line:max-line-length
    this.configuration.apiKeys['Authorization'] = 'Bearer eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIxNTQwMTI2MDg5NjYxLTc3MTJlZDg0LTRhOWMtNDZhMy1hMWE1LTBiYzQxNzYxOGQzNSIsImlhdCI6MTU0MDEyNjA4OSwic3ViIjoib3ZlcnNpZ3QtYXBpIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdC9hcGkvdjEiLCJ1c2VybmFtZSI6InVzZXIxIiwiZXhwIjoxNjQwMTQwNDg5fQ.laAyfPSKlGmXhebQsNQaLxZHss-s83Ls16e8lUc7WAs'; // TODO replace by user login
  }

  isShowingConfiguration(): boolean {
    const url = this.router.url;
    return url.startsWith('/config/') || url === '/config';
  }
}
