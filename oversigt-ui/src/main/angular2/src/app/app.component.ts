import { Component, OnInit } from '@angular/core';
import { Configuration } from 'src/oversigt-client';
import { ActivatedRoute, Router } from '@angular/router';
import { environment } from 'src/environments/environment.prod';

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
    this.configuration.apiKeys['Authorization'] = environment.authorizationKey;
  }

  isShowingConfiguration(): boolean {
    const url = this.router.url;
    return url.startsWith('/config/') || url === '/config';
  }
}
